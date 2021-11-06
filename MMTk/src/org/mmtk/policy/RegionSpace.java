package org.mmtk.policy;

import static org.mmtk.utility.Constants.*;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.*;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.*;

public class RegionSpace extends Space {

    // TODO
    private static final int META_DATA_PAGES_PER_REGION = CARD_META_PAGES_PER_REGION;
    public static int REGION_SIZE = 32768; // TODO: size

    public RegionSpace(String name, VMRequest vmRequest) {
        this(name, true, vmRequest);
    }

    protected RegionSpace(String name, boolean zeroed, VMRequest vmRequest) {
        super(name, true, false, zeroed, vmRequest);
        if (vmRequest.isDiscontiguous()) {
            pr = new MonotonePageResource(this, META_DATA_PAGES_PER_REGION);
        } else {
            pr = new MonotonePageResource(this, start, extent, META_DATA_PAGES_PER_REGION);
        }
        // TODO Auto-generated constructor stub
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

    public Address getRegion() {
        // TODO
        return Address.zero();
    }

    /**
     * Return the region this object belongs to.
     * 
     * @param object
     * @return
     */
    @Inline
    public Address regionOf(ObjectReference object) {
        // TODO
        return Address.zero();
    }

    @Inline
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object, int allocator) {
        // TODO

        return object;
    }

}
