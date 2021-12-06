package org.mmtk.utility.alloc;

import org.mmtk.policy.RegionSpace;
import org.mmtk.policy.Space;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import static org.mmtk.utility.Constants.BYTES_IN_ADDRESS;
import static org.mmtk.utility.Constants.MIN_ALIGNMENT;

/**
 * Region layout
 * +-------------+--------------+-------------
 * |  Data End   | (Relocation) | Data --&gt
 * +-------------+--------------+-------------
 */
@Uninterruptible public class RegionAllocator extends Allocator {

    // private static final Word BLOCK_MASK = null;

    /** space this bump pointer is associated with */
    protected RegionSpace space;

    /** insertion point */
    protected Address cursor;
    /** current sentinel for bump pointer */
    private Address limit;
    /** current contiguous region */
    protected Address region;

    public static final Offset DATA_END_OFFSET = Offset.zero();
    public static final Offset DATA_START_OFFSET = alignAllocationNoFill(
            Address.zero().plus(DATA_END_OFFSET.plus(BYTES_IN_ADDRESS)), MIN_ALIGNMENT, 0).toWord().toOffset();

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
        setDataEnd(region, cursor);
        return start;
    }

    @NoInline
    private Address allocSlow(Address start, Address end, int align, int offset) {
        return allocSlowInline(end.diff(start).toInt(), align, offset);
    }

    /**
     * Called by allocSlowInline
     */
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
        updateMetaData(start);
        limit = start.plus(RegionSpace.REGION_SIZE);
        return alloc(bytes, alignment, offset);
    }

    /**
     * Update the metadata to reflect the addition of a new region.
     *
     * @param start The start of the new region
     */
    @Inline
    private void updateMetaData(Address start) {
        setDataEnd(region, cursor); // data end of current region

        region = start; // setup new region
        cursor = region.plus(DATA_START_OFFSET);
    }

    /**
     * Set the data end of the given region.
     *
     * @param region
     * @param end The end of the current region
     */
    public static void setDataEnd(Address region, Address endAddress) {
        region.store(endAddress, DATA_END_OFFSET);
    }

    /**
     * Return the data end of a region. 
     * 
     * @param region
     * @return the data end of the region
     */
    @Inline
    public static Address getDataEnd(Address region) {
        return region.plus(DATA_END_OFFSET).loadAddress();
    }
}