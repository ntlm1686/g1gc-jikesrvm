package org.mmtk.policy;

import static org.mmtk.utility.Constants.*;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.*;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public final class G1Space extends Space {

    private static final int META_DATA_PAGES_PER_REGION = CARD_META_PAGES_PER_REGION;

    public G1Space(String name, VMRequest vmRequest) {
        super(name, true, false, true, vmRequest);
        if (vmRequest.isDiscontiguous()) {
            pr = new MonotonePageResource(this, META_DATA_PAGES_PER_REGION);
        } else {
            pr = new MonotonePageResource(this, start, extent, META_DATA_PAGES_PER_REGION);
        }
    }

    /**
     * Prepare this space instance for a collection.
     */
    public void prepare() {

    }

    /**
     * Relase the entire space.
     */
    public void release() {
        // TODO Auto-generated method stub

    }

    /**
     * Release an allocated page or pages.
     *
     * @param start The address of the start of the page or pages
     */
    @Override
    @Inline
    public void release(Address start) {
        // TODO
    }

    @Override
    @Inline
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isLive(ObjectReference object) {
        // TODO Auto-generated method stub
        return false;
    }

}