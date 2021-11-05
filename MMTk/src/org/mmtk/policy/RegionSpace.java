package org.mmtk.policy;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.unboxed.*;
public class RegionSpace extends Space{

    public static int REGION_SIZE = 32768; // TODO: size

    protected RegionSpace(String name, boolean movable, boolean immortal, boolean zeroed, VMRequest vmRequest) {
        super(name, movable, immortal, zeroed, vmRequest);
        //TODO Auto-generated constructor stub
    }

    @Override
    public void release(Address start) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isLive(ObjectReference object) {
        // TODO Auto-generated method stub
        return false;
    }
    
    public Address getRegion(int size){
        Address tlab = allocTLABFast(size);
        if (!tlab.isZero()) return tlab;
        return allocTLABSlow(size);
    }

    public Address allocTLABFast(int size) {
        return Address.zero();
    }

    public Address allocTLABSlow(int size) {
        return Address.zero();
    }
}
