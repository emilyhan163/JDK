/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.jvm.hotspot.gc.g1;

import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for G1HeapRegionTable. It's essentially an index -> HeapRegion map.

public class G1HeapRegionTable extends VMObject {
    // HeapRegion** _base;
    static private AddressField baseField;
    // uint _length;
    static private CIntegerField lengthField;
    // HeapRegion** _biased_base
    static private AddressField biasedBaseField;
    // size_t _bias
    static private CIntegerField biasField;
    // uint _shift_by
    static private CIntegerField shiftByField;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("G1HeapRegionTable");

        baseField = type.getAddressField("_base");
        lengthField = type.getCIntegerField("_length");
        biasedBaseField = type.getAddressField("_biased_base");
        biasField = type.getCIntegerField("_bias");
        shiftByField = type.getCIntegerField("_shift_by");
    }

    private HeapRegion at(long index) {
        Address arrayAddr = baseField.getValue(addr);
        // Offset of &_base[index]
        long offset = index * VM.getVM().getAddressSize();
        Address regionAddr = arrayAddr.getAddressAt(offset);
        return (HeapRegion) VMObjectFactory.newObject(HeapRegion.class,
                                                      regionAddr);
    }

    public long length() {
        return lengthField.getValue(addr);
    }

    public long bias() {
        return biasField.getValue(addr);
    }

    public long shiftBy() {
        return shiftByField.getValue(addr);
    }

    private class HeapRegionIterator implements Iterator<HeapRegion> {
        private long index;
        private long length;
        private HeapRegion next;

        public HeapRegion positionToNext() {
          HeapRegion result = next;
          while (index < length && at(index) == null) {
            index++;
          }
          if (index < length) {
            next = at(index);
            index++; // restart search at next element
          } else {
            next = null;
          }
          return result;
        }

        @Override
        public boolean hasNext() { return next != null;     }

        @Override
        public HeapRegion next() { return positionToNext(); }

        @Override
        public void remove()     { /* not supported */      }

        HeapRegionIterator(long totalLength) {
            index = 0;
            length = totalLength;
            positionToNext();
        }
    }

    public Iterator<HeapRegion> heapRegionIterator(long committedLength) {
        return new HeapRegionIterator(committedLength);
    }

    public G1HeapRegionTable(Address addr) {
        super(addr);
    }

    public HeapRegion getByAddress(Address target) {
        Address arrayAddr = biasedBaseField.getValue(addr);
        long biasedIndex = target.asLongValue() >>> shiftBy();
        long offset = biasedIndex * HeapRegion.getPointerSize();
        Address regionAddr = arrayAddr.getAddressAt(offset);
        return (HeapRegion)VMObjectFactory.newObject(HeapRegion.class, regionAddr);
    }
}
