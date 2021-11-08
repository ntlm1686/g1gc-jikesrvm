package org.mmtk.policy;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Comparator;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.*;

public class RegionSpace extends Space {

    public static final boolean HEADER_MARK_BITS = VM.config.HEADER_MARK_BITS;
    /** highest bit bits we may use */
    private static final int AVAILABLE_LOCAL_BITS = 8 - HeaderByte.USED_GLOBAL_BITS;

    private static final int COUNT_BASE = 0;

    /** Mark bits */
    public static final int DEFAULT_MARKCOUNT_BITS = 4;
    public static final int MAX_MARKCOUNT_BITS = AVAILABLE_LOCAL_BITS - COUNT_BASE;
    private static final byte MARK_COUNT_INCREMENT = (byte) (1 << COUNT_BASE);
    private static final byte MARK_COUNT_MASK = (byte) (((1 << MAX_MARKCOUNT_BITS) - 1) << COUNT_BASE); // minus 1 for
                                                                                                        // copy/alloc

    private byte markState = 1;
    private byte allocState = 0;
    private boolean isAllocAsMarked = false;

    // TODO
    private static final int META_DATA_PAGES_PER_REGION = 0;
    public static final int PAGES_PER_REGION = 256;
    public static final int REGION_SIZE = 32768; // TODO: size
    public static final int REGION_NUMBER = 1000;

    protected final Lock lock = VM.newLock("RegionSpaceGloabl");
    // TODO replace with shared queue?
    protected final AddressArray regionTable = AddressArray.create(REGION_NUMBER);
    protected final AddressArray availableRegion = AddressArray.create(REGION_NUMBER);

    // deprecated
    protected final AddressArray consumedRegion = AddressArray.create(REGION_NUMBER);

    int availableRegionCount = 0;
    int consumedRegionCount = 0;

    // Before we implement the metadata, we use a map instead
    protected final Map<Address, Integer> regionLiveBytes = new HashMap<Address, Integer>();
    protected final Map<Address, Boolean> requireRelocation = new HashMap<Address, Boolean>();

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
        this.resetRequireRelocation();
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
    @Inline
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        VM.assertions.fail("CopySpace.traceLocal called without allocator");
        return ObjectReference.nullReference();
    }

    @Override
    @Inline
    public boolean isLive(ObjectReference object) {
        return testMarkState(object);
    }

    /**
     * Prepare for the next GC.
     */
    public void prepare() {
    }

    /**
     * Return a new region to the allocator.
     * 
     * @return
     */
    @Inline
    public Address getRegion() {
        lock.acquire();
        if (availableRegionCount == 0) {
            // No available region
            lock.release();
            return Address.zero();
        }
        Address newRegion = availableRegion.get(availableRegionCount);
        availableRegionCount--;
        consumedRegionCount++;
        consumedRegion.set(consumedRegionCount, newRegion);
        lock.release();
        return newRegion;
    }

    /**
     * Add a new region to this space.
     */
    @Inline
    private Address addRegion() {
        Address newRegion = this.acquire(PAGES_PER_REGION);
        return newRegion;
    }

    /**
     * Initialize the regions.
     */
    @Inline
    private void initializeRegions() {
        for (int i = 0; i < REGION_NUMBER; i++) {
            Address region = addRegion();
            regionTable.set(i, region);
            availableRegion.set(i, region);
            consumedRegion.set(i, Address.zero());
        }
        Address[] regionTableCopy = regionTable.getAll();
        Arrays.sort(regionTableCopy, new Comparator<Address>() {
            @Override
            public int compare(Address o1, Addresso2) {
                return (Integer.parseInt(o2.toInt())).compareTo(Integer.parseInt(o1.toInt()));
            }
        });
        regionTable.setAll(regionTableCopy);
    }

    @Inline
    private boolean isRegionIdeal(Address X, Address Y) {
        return (X.toInt() < Y.toInt()) && (X.toInt() + REGION_SIZE >= Y.toInt());
    }

    @Inline
    private Address idealRegion(Address[] table, Address address) {
        for (Address region : table)
            if(isRegionIdeal(region, address))
                return region;
    }

    /**
     * Return the region this object belongs to.
     * 
     * @param object
     * @return
     */
    @Inline
    public Address regionOf(ObjectReference object) {
        Address address = object.toAddress();
        return idealRegion(regionTable.getAll(), address);
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
     * Perform any required post allocation initialization
     *
     * @param object the object ref to the storage to be initialized
     */
    @Inline
    public void postAlloc(ObjectReference object) {
        initializeHeader(object, true);
    }

    /**
     * Perform any required post copy (i.e. in-GC allocation) initialization. This
     * is relevant (for example) when MS is used as the mature space in a copying
     * GC.
     *
     * @param object  the object ref to the storage to be initialized
     * @param majorGC Is this copy happening during a major gc?
     */
    @Inline
    public void postCopy(ObjectReference object, boolean majorGC) {
        initializeHeader(object, false);
    }

    /**
     * Update the collection set, based on the region liveness.
     * 
     * @param trace
     * @param object
     * @param allocator
     * @return
     */
    @Inline
    public void updateCollectionSet() {
        // TODO
    }

    /**
     * Evacuate a region using linear scan.
     * 
     * @param region
     */
    @Inline
    public void evacuateRegion(Address region) {
        // linear scan a region
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
                assert (regionLiveBytes.get(regionOf(object)) != 0);

                // object is not being forwarded, copy it
                ObjectReference newObject = VM.objectModel.copy(object, allocator);
                ForwardingWord.setForwardingPointer(object, newObject);
                trace.processNode(newObject);

                // TODO lock
                int newLiveBytes = regionLiveBytes.get(regionOf(object)) - sizeOf(object);
                regionLiveBytes.put(regionOf(object), newLiveBytes);
                if (newLiveBytes == 0) {
                    // if new live bytes is 0, the region is empty, it's available again
                    lock.acquire();
                    availableRegionCount++;
                    availableRegion.set(availableRegionCount, regionOf(object));
                    lock.release();
                }

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
     * Atomically attempt to set the mark bit of an object.
     * 
     * @param object
     * @return true if sucessfully set, false if already set
     */
    @Inline
    private boolean testAndMark(ObjectReference object) {
        byte oldValue, markBits, newValue;
        oldValue = VM.objectModel.readAvailableByte(object);
        markBits = (byte) (oldValue & MARK_COUNT_MASK);
        if (markBits == markState)
            return false;
        newValue = (byte) ((oldValue & ~MARK_COUNT_MASK) | markState);
        if (HeaderByte.NEEDS_UNLOGGED_BIT)
            newValue |= HeaderByte.UNLOGGED_BIT;
        VM.objectModel.writeAvailableByte(object, newValue);
        return true;
    }

    /**
     * Perform any required initialization of the GC portion of the header.
     *
     * @param object the object ref to the storage to be initialized
     * @param alloc  is this initialization occuring due to (initial) allocation
     *               (true) or due to copying (false)?
     */
    @Inline
    public void initializeHeader(ObjectReference object, boolean alloc) {
        byte oldValue = VM.objectModel.readAvailableByte(object);
        byte newValue = (byte) ((oldValue & ~MARK_COUNT_MASK) | (alloc && !isAllocAsMarked ? allocState : markState));
        VM.objectModel.writeAvailableByte(object, newValue);
    }

    /**
     * Check the mark bit of an object.
     * 
     * @param object
     * @return true if marked, false if not
     */
    @Inline
    private boolean testMarkState(ObjectReference object) {
        if (VM.VERIFY_ASSERTIONS)
            VM.assertions._assert((markState & ~MARK_COUNT_MASK) == 0);
        return (VM.objectModel.readAvailableByte(object) & MARK_COUNT_MASK) == markState;
    }

    /**
     * Look into the region's flag bits. collection set
     * 
     * (this can be implemented in RegionAllocator)
     * 
     * @param region
     * @return
     */
    @Inline
    private boolean relocationRequired(Address region) {
        return relocationRequired(region);
    }

    @Inline
    private boolean relocationRequired(ObjectReference object) {
        return relocationRequired(regionOf(object));
    }

    /**
     * Set all regions' live bytes to 0.
     */
    @Inline
    private void resetRegionLiveBytes() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            regionLiveBytes.put(regionTable.get(i), 0);
        }
    }

    /**
     * All regions do not require relocation.
     */
    @Inline
    private void resetRequireRelocation() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            requireRelocation.put(regionTable.get(i), false);
        }
    }

    /**
     * Update the live bytes of a region by adding the size of the object.
     * 
     * @param region
     * @param object
     */
    @Inline
    private void updateRegionliveBytes(Address region, ObjectReference object) {
        // update the region's live bytes
        regionLiveBytes.put(region, sizeOf(object) + regionLiveBytes.get(region));
    }

    /**
     * Return the size of an object.
     */
    @Inline
    private int sizeOf(ObjectReference object) {
        Address objectStart = object.toAddress();
        Address objectEnd = VM.objectModel.getObjectEndAddress(object);
        Offset size = objectStart.diff(objectEnd);
        int liveBytes = size.toInt();
        return liveBytes;
    }
}