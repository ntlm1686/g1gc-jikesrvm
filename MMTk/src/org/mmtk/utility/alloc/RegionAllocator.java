package org.mmtk.utility.alloc;

import org.mmtk.policy.RegionSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class RegionAllocator extends Allocator {

    // private static final Word BLOCK_MASK = null;
    

    /** space this bump pointer is associated with */
    protected RegionSpace space;

    /** insertion point */
    protected Address cursor;
    /** current sentinel for bump pointer */
    private Address limit;
    /** current contiguous region */
    protected Address region;
    
    // TODO let the space manage the regions
    /** first contiguous region */
    // protected Address initialRegion;
    /** linear scanning is permitted if true */


    protected RegionAllocator(RegionSpace space) {
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
        region = Address.zero();
    }

    @Override
    protected Space getSpace() {
        return space;
    }

    @Inline
    public final Address alloc(int bytes, int align, int offset) {
        Address start = alignAllocationNoFill(cursor, align, offset);
        Address end = start.plus(bytes);

        // can this object fit in the current region?
        if (end.GT(limit))
            return allocSlow(start, end, align, offset);

        fillAlignmentGap(cursor, start);
        cursor = end;

        // TODO record the allocation
        // space.setCursor(currentRegion, cursor);
        return start;
    }

    private Address allocSlow(Address start, Address end, int align, int offset) {
        return allocSlowInline(end.diff(start).toInt(), align, offset);
    }

    @Override
    protected Address allocSlowOnce(int bytes, int alignment, int offset) {
        if (!cursor.isZero()) {
            this.reset();
        }
        // get a new region
        Address start = space.getRegion();
        
        if (start.isZero())
        return start; // failed allocation
        
        // assume the region is not contiguous
        cursor = start;

        // update limit
        limit = start.plus(RegionSpace.REGION_SIZE);

        return alloc(bytes, alignment, offset);
    }
}
