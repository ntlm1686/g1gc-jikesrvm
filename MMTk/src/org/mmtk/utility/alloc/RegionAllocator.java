package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.policy.RegionSpace;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class RegionAllocator extends Allocator {

    /** insertion point */
    protected Address cursor;
    /** current internal slow-path sentinel for bump pointer */
    private Address internalLimit;
    /** current external slow-path sentinel for bump pointer */
    private Address limit;
    /** space this bump pointer is associated with */
    protected Space space;
    /** first contiguous region */
    protected Address initialRegion;
    /** linear scanning is permitted if true */
    // protected final boolean allowScanning;
    /** current contiguous region */
    protected Address region;

    protected RegionAllocator(Space space, boolean allowScanning) {
        this.space = space;
        reset();
    }

    /**
     * Reset the allocator. Note that this does not reset the space. This is must be
     * done by the caller.
     */
    public final void reset() {
        cursor = Address.zero();
        limit = Address.zero();
        internalLimit = Address.zero();
        initialRegion = Address.zero();
        region = Address.zero();
    }

    @Override
    protected Space getSpace() {
        // TODO Auto-generated method stub
        return null;
    }

    @Inline
    public final Address alloc(int bytes, int align, int offset) {
        Address start = alignAllocationNoFill(cursor, align, offset);
        Address end = start.plus(bytes);

        // can this object fit in the current region?
        if (end.GT(limit))
            return allocSlowInline(bytes, align, offset);

        fillAlignmentGap(cursor, start);
        cursor = end;

        // record the allocation
        // Region.setCursor(currentRegion, cursor);
        return start;
    }

    @Override
    protected Address allocSlowOnce(int bytes, int alignment, int offset) {
        region = Address.zero(); // require a new region from space

        if (region.isZero()) {
            // space has no more region
            return region;
        }

        cursor = region;
        limit = region.plus(RegionSpace.REGION_SIZE);
        return alloc(bytes, alignment, offset);
    }

}
