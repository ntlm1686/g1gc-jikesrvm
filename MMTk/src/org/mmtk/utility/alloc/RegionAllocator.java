package org.mmtk.utility.alloc;

import org.mmtk.policy.RegionSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
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

    private static final int SIZE_OF_TWO_X86_CACHE_LINES_IN_BYTES = 128;

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
        // Log.writeln("[alloc] enter: ", bytes);
        Address start = alignAllocationNoFill(cursor, align, offset);
        Address end = start.plus(bytes);

        // can this object fit in the current region?
        // Log.writeln("[alloc] limit: ", limit.toInt());
        // Log.writeln("[alloc] end: ", end.toInt());
        if (end.GT(limit)){
            Log.writeln("[alloc] go to slow path");
            return allocSlow(start, end, align, offset);
        }

        fillAlignmentGap(cursor, start);
        // Log.write("[alloc] cursor: " ,cursor.toInt());
        cursor = end;
        end.plus(SIZE_OF_TWO_X86_CACHE_LINES_IN_BYTES).prefetch();
        // Log.writeln(" -> ", cursor.toInt());
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
        Log.writeln("[allocSlowOnce] enter");

        Log.writeln("[allocSlowOnce] try to get region from pool");
        Address start = space.getRegion();
        if (start.isZero()) {
            Log.writeln("[allocSlowOnce] pool has no region, exit");
            return start; // failed allocation
        }

        Log.writeln("[allocSlowOnce] pool gave a new region");

        // update limit
        updateMetaData(start);
        cursor = region.plus(DATA_START_OFFSET);
        limit = start.plus(RegionSpace.REGION_EXTENT);
        Log.writeln("[allocSlowOnce] new limit: ", limit.toInt());


        Log.writeln("[allocSlowOnce] region info updated, entering alloc");
        return alloc(bytes, alignment, offset);
    }

    /**
     * Update the metadata to reflect the addition of a new region.
     *
     * @param start The start of the new region
     */
    @Inline
    private void updateMetaData(Address start) {
        Log.writeln("[updateMetaData] enter");
        region = start; // setup new region
        setDataEnd(region, cursor); // data end of current region
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