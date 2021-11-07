package org.mmtk.policy;

import static org.mmtk.utility.Constants.*;

import java.util.HashMap;
import java.util.Map;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.*;

public class RegionSpace extends Space {

    // TODO
    private static final int META_DATA_PAGES_PER_REGION = 0;
    public static final int PAGES_PER_REGION = 256;
    public static final int REGION_SIZE = 32768; // TODO: size
    public static final int REGION_NUMBER = 1000;

    protected final AddressArray regionTable = AddressArray.create(REGION_NUMBER);
    protected final AddressArray availableRegion = AddressArray.create(REGION_NUMBER);
    protected final AddressArray consumedRegion = AddressArray.create(REGION_NUMBER);

    // Before we implement the metadata, we use a map instead
    protected final Map<Address, Integer> regionLiveBytes = new HashMap<Address, Integer>();

    public RegionSpace(String name, VMRequest vmRequest) {
        this(name, true, vmRequest);
    }

    public RegionSpace(String name, boolean zeroed, VMRequest vmRequest) {
        super(name, true, false, zeroed, vmRequest);
        if (vmRequest.isDiscontiguous()) {
            pr = new MonotonePageResource(this, META_DATA_PAGES_PER_REGION);
        } else {
            pr = new MonotonePageResource(this, start, extent, META_DATA_PAGES_PER_REGION);
        }

        this.initializeRegions();
        this.resetRegionLiveBytes();
    }

    /**
     * Release allocated pages.
     */
    @Override
    @Inline
    public void release(Address start) {
        ((FreeListPageResource) pr).releasePages(start);
    }

    @Override
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        VM.assertions.fail("CopySpace.traceLocal called without allocator");
        return ObjectReference.nullReference();
    }

    @Override
    public boolean isLive(ObjectReference object) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Return a new region to the allocator.
     * 
     * @return
     */
    public Address getRegion() {

        // TODO initialize the region, maybe record this region
        return this.acquire(PAGES_PER_REGION);
    }

    /**
     * Add a new region to this space.
     */
    private Address addRegion() {
        Address newRegion = this.acquire(PAGES_PER_REGION);

        return newRegion;
    }

    /**
     * Initialize the regions.
     */
    private void initializeRegions() {
        for (int i = 0; i < REGION_NUMBER; i++) {
            Address region = addRegion();
            regionTable.set(i, region);
            availableRegion.set(i, region);
            consumedRegion.set(i, Address.zero());
        }
    }

    /**
     * Return the region this object belongs to.
     * 
     * @param object
     * @return
     */
    @Inline
    public Address regionOf(ObjectReference object) {
        // TODO Maybe use binary search to find the region of this object
        Address addr = object.toAddress();
        // region address array

        return Address.zero();
    }

    /**
     * Full heap tracing. Trace and mark all live objects, and set the bitmap for
     * liveness of its region.
     * 
     * @param trace
     * @param object
     * @param allocator
     */
    @Inline
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object, int allocator) {
        if (testAndMark(object)) {
            Address region = regionOf(object);
            updateRegionliveBytes(region, object);
            trace.processNode(object);
        }
        return object;
    }


    /**
     * Update the collection set, based on the region liveness.
     * 
     * @param trace
     * @param object
     * @param allocator
     * @return
     */
    public void updateCollectionSet() {
        // TODO
    }

    /**
     * Another full heap tracing. Copying all live objects in selected regions.
     * 
     * @param trace
     * @param object
     * @param allocator
     * @return return new object if moved, otherwise return original object
     */
    @Inline
    public ObjectReference traceEvcauateObject(TransitiveClosure trace, ObjectReference object, int allocator) {
        if (relocationRequired(regionOf(object))) {
            Word forwardingWord = ForwardingWord.attemptToForward(object);
            if (ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
                // object is being forwarded by other thread, after it finished, return the copy
                while (ForwardingWord.stateIsBeingForwarded(forwardingWord))
                    forwardingWord = VM.objectModel.readAvailableBitsWord(object);
                // no processNode since it's been pushed by other thread
                return ForwardingWord.extractForwardingPointer(forwardingWord);
            } else {
                // object is not being forwarded, copy it
                ObjectReference newObject = VM.objectModel.copy(object, allocator);
                ForwardingWord.setForwardingPointer(object, newObject);
                trace.processNode(newObject);
                return newObject;
            }
        } else {
            if (testAndMark(object)) {
                trace.processNode(object);
            }
        }
        // object is not in the collection set
        return object;
    }

    /**
     * test and set the live bit for this object in its region's metadata
     * 
     * @param object
     * @return true if sucessfully set, false if already set
     */
    @Inline
    private boolean testAndMark(ObjectReference object) {
        return false;
    }

    /**
     * Look into the region's flag bits.
     * collection set
     * 
     * (this can be implemented in RegionAllocator)
     * 
     * @param regionOf
     * @return
     */
    @Inline
    private boolean relocationRequired(Address region) {
        // TODO

        return false;
    }

    @Inline
    private void resetRegionLiveBytes() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            regionLiveBytes.put(regionTable.get(i), 0);
        }
    }

    @Inline
    private void updateRegionliveBytes(Address region, int liveBytes) {
        regionLiveBytes.put(region, liveBytes);
    }

    @Inline
    private void updateRegionliveBytes(Address region, ObjectReference object) {
        int liveBytes = 0;
        // liveBytes = sizeOf(object);
        regionLiveBytes.put(region, liveBytes);
    }
}
