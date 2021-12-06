package org.mmtk.policy;

import java.util.*;

import static org.mmtk.utility.Constants.BYTES_IN_PAGE;
import static org.mmtk.utility.alloc.RegionAllocator.DATA_START_OFFSET;

import org.mmtk.utility.options.Options;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.*;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.RegionAllocator;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.*;

import org.vmmagic.pragma.Uninterruptible;

public class RegionSpace extends Space {

    // Constraints
    public static final int LOCAL_GC_BITS_REQUIRED = 2;
    public static final int GC_HEADER_WORDS_REQUIRED = 0;

    public static final boolean HEADER_MARK_BITS = VM.config.HEADER_MARK_BITS;
    /**
     * highest bit bits we may use
     */
    private static final int AVAILABLE_LOCAL_BITS = 8 - HeaderByte.USED_GLOBAL_BITS;

    private static final int COUNT_BASE = 0;

    /**
     * Mark bits
     */
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
    public static final int REGION_SIZE = BYTES_IN_PAGE * PAGES_PER_REGION;
    public static final int REGION_NUMBER = 1000;

    protected final Lock lock = VM.newLock("RegionSpaceGloabl");
    // TODO replace with shared queue?
    protected final AddressArray regionTable = AddressArray.create(REGION_NUMBER);
    protected final AddressArray availableRegion = AddressArray.create(REGION_NUMBER);

    // deprecated
    protected final AddressArray consumedRegion = AddressArray.create(REGION_NUMBER);

    int availableRegionCount = 0;

    // Before we implement the metadata, we use a map instead
    // protected static Map<Address, Integer> regionLiveBytes = new HashMap<Address, Integer>();
    // DeadBytes for every regio that is calcuoated before evacuation
    // protected static Map<Address, Integer> regionDeadBytes = new HashMap<Address, Integer>();

    // Regions on which garbage collector will be executed
    protected static List<Integer> collectionSet = new ArrayList<Integer>();

    protected static Map<Integer, Integer> regionLiveBytes = new HashMap<Integer, Integer>();
    protected static Map<Integer, Integer> regionDeadBytes = new HashMap<Integer, Integer>();



    // protected final Map<Address, Boolean> requireRelocation = new HashMap<Address, Boolean>();
    protected final Map<Integer, Boolean> requireRelocation = new HashMap<Integer, Boolean>();

    // constructor
    public RegionSpace(String name, VMRequest vmRequest) {
        this(name, true, vmRequest);
    }

    // helps for linear scan
    // Data must start particle-aligned.

    // private constructor
    private RegionSpace(String name, boolean zeroed, VMRequest vmRequest) {
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
    @Uninterruptible
    public void release(Address start) {
        ((FreeListPageResource) pr).releasePages(start);
    }

    /**
     * Release pages
     */
    @Inline
    public void release() {
        for (Integer regionAddress : collectionSet) {
            release(Address.fromLong(new Long(regionAddress)));
            availableRegionCount++;
            assert availableRegionCount < REGION_NUMBER;
            availableRegion.set(availableRegionCount, Address.fromLong(new Long(regionAddress)));
        }
    }

    @Override
    @Inline
    @Uninterruptible
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        VM.assertions.fail("CopySpace.traceLocal called without allocator");
        return ObjectReference.nullReference();
    }

    /**
     * If an object is alive.
     */
    @Override
    @Inline
    @Uninterruptible
    public boolean isLive(ObjectReference object) {
        return testMarkState(object);
    }

    /**
     * Prepare for the next GC.
     */
    public void prepare() {
        // flip the mark bit
        allocState = markState;
        markState = deltaMarkState(true);

        // reset the regions' info

        this.resetRegionLiveBytes();
        this.resetRequireRelocation();
        this.resetRegionDeadBytes();

        collectionSet.clear();
    }

    /**
     * Return a new region to the allocator.
     *
     * @return
     */
    public Address getRegion() {
        lock.acquire();
        if (availableRegionCount == 0) {
            // No available region
            lock.release();
            return Address.zero();
        }
        Address newRegion = availableRegion.get(availableRegionCount);
        availableRegionCount--;
        lock.release();
        return newRegion;
    }

    /**
     * Add a new region to this space.
     */
    @Inline
    private Address addRegion() {
        Address newRegion = acquire(PAGES_PER_REGION);
        return newRegion;
    }


    private void sortTable() {
        AddressArray sortedRegionTable = AddressArray.create(REGION_NUMBER);
        for (int i = 0; i < REGION_NUMBER; i++) {
            Address regionAddress = regionTable.get(i);
            sortedRegionTable.set(i, regionAddress);
        }
        boolean sorted = false;
        while(!sorted) {
            sorted = true;
            for (int i = 0; i < REGION_NUMBER - 1; i++) {
                if (sortedRegionTable.get(i).toLong() > sortedRegionTable.get(i+1).toLong()) {
                    sortedRegionTable.set(i, Address.fromLong(sortedRegionTable.get(i).toLong() + sortedRegionTable.get(i+1).toLong()));
                    sortedRegionTable.set(i+1, Address.fromLong(sortedRegionTable.get(i).toLong() - sortedRegionTable.get(i+1).toLong()));
                    sortedRegionTable.set(i, Address.fromLong(sortedRegionTable.get(i).toLong() - sortedRegionTable.get(i+1).toLong()));
                    sorted = false;
                }
            }
        }
        for (int i = 0; i < REGION_NUMBER; i++) {
            Address regionAddress = sortedRegionTable.get(i);
            regionTable.set(i, regionAddress);
        }
    }

    /**
     * Initialize the regions.
     */
    @Inline
    private void initializeRegions() {
        for (int i = 0; i < REGION_NUMBER; i++) {
            Address region = Address.zero();
            availableRegionCount++;
            regionTable.set(i, region);
            availableRegion.set(i, region);
        }
        this.sortTable();
    }

    private boolean isRegionIdeal(Address X, Address Y) {
        return (X.toInt() < Y.toInt()) && (X.toInt() + REGION_SIZE >= Y.toInt());
    }

    private Address idealRegion(Address address) {
        int left = 0;
        int right = REGION_NUMBER - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            if (this.isRegionIdeal(regionTable.get(mid), address)) {
                return regionTable.get(mid);
            }
            if (regionTable.get(mid).toInt() > address.toInt()) {
                right = mid - 1;
            } else if (regionTable.get(mid).toInt() + REGION_SIZE < address.toInt()) {
                left = mid + 1;
            }
        }
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
        Address address = object.toAddress();
        return this.idealRegion(address);
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
     * @param object the object ref to the storage to be initialized
     */
    @Inline
    public void postCopy(ObjectReference object) {
        initializeHeader(object, false);
    }

    /**
     * Author: Mahideep Tumati
     * Create a list of Adress on which GC needs to be implemented
     *
     * @param trace
     * @param object
     * @param allocator
     * @return
     */
    @Inline
    public void updateCollectionSet() {
        // calculate dead Bytes from lives
        updateDeadBytesInformation();
        regionDeadBytes = sortAddressMapByValueDesc(regionDeadBytes);

        int totalAvailableBytes = REGION_SIZE * availableRegionCount;

        int counter = 0;
        for (Map.Entry<Integer, Integer> region : regionDeadBytes.entrySet()) {
            if (regionLiveBytes.get(region.getKey()) <= totalAvailableBytes) {
                if (counter < availableRegionCount) {
                    collectionSet.add(region.getKey());
                    requireRelocation.put(region.getKey(), true);
                    // totalAvailableBytes -= regionLiveBytes.get(region.getKey());
                    counter++;
                } else {
                    break;
                }
            }
        }
    }
     @Inline
     public void updateDeadBytesInformation() {
        for (Map.Entry<Integer, Integer> addressEntry : regionLiveBytes.entrySet()) {
            Address dataEnd = BumpPointer.getDataEnd(Address.fromLong(new Long(addressEntry.getKey())));
            regionDeadBytes.put(addressEntry.getKey(),
                    (dataEnd.toInt() - addressEntry.getKey()) - addressEntry.getValue());
        }
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
     * Return the mark state incremented or decremented by one.
     *
     * @param increment If true, then return the incremented value else return the
     *                  decremented value
     * @return the mark state incremented or decremented by one.
     */
    private byte deltaMarkState(boolean increment) {
        byte mask = (byte) (((1 << Options.markSweepMarkBits.getValue()) - 1) << COUNT_BASE);
        byte rtn = (byte) (increment ? markState + MARK_COUNT_INCREMENT : markState - MARK_COUNT_INCREMENT);
        rtn &= mask;
        if (VM.VERIFY_ASSERTIONS)
            VM.assertions._assert((markState & ~MARK_COUNT_MASK) == 0);
        return rtn;
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
     * <p>
     * (this can be implemented in RegionAllocator)
     *
     * @param region
     * @return
     */
    public boolean relocationRequired(ObjectReference object) {
        return Boolean.TRUE.equals(requireRelocation.get(new Integer(regionOf(object).toInt())));
    }

    /**
     * Set all regions' live bytes to 0.
     */
    @Inline
    private void resetRegionLiveBytes() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            regionLiveBytes.put(Address.zero().toInt(), 0);
        }
    }

    /**
     * Set all regions' dead bytes to 0. (may not necessary)
     */
    @Inline
    private void resetRegionDeadBytes() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            regionDeadBytes.put(regionTable.get(i).toInt(), 0);
        }
    }

    /**
     * All regions do not require relocation.
     */
    @Inline
    private void resetRequireRelocation() {
        // assert regionTable has been initialized
        for (int i = 0; i < REGION_NUMBER; i++) {
            requireRelocation.put(regionTable.get(i).toInt(), false);
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
        regionLiveBytes.put(region.toInt(), sizeOf(object) + regionLiveBytes.get(region.toInt()));
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

    /**
     * Author: Mahideep Tumati
     * <p>
     * Sort a hashmap based on value.
     *
     * @param Map of regions with key as start address and value as live/ dead bytes
     * @return Map of regions sorted based on value
     */
    public static Map<Integer, Integer> sortAddressMapByValueDesc(Map<Integer, Integer> addressMap) {
        List<Map.Entry<Integer, Integer>> list = new LinkedList<Map.Entry<Integer, Integer>>(addressMap.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1,
                    Map.Entry<Integer, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        Map<Integer, Integer> tempMap = new LinkedHashMap<Integer, Integer>();
        for (Map.Entry<Integer, Integer> address : list) {
            tempMap.put(address.getKey(), address.getValue());
        }
        return tempMap;
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
    public ObjectReference traceEvacuateObject(TransitiveClosure trace, ObjectReference object, int allocator) {

        if (relocationRequired(object)) {
            Word forwardingWord = ForwardingWord.attemptToForward(object);
            if (ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
                while (ForwardingWord.stateIsBeingForwarded(forwardingWord))
                    forwardingWord = VM.objectModel.readAvailableBitsWord(object);
                return ForwardingWord.extractForwardingPointer(forwardingWord);
            } else {
                if (VM.VERIFY_ASSERTIONS)
                    VM.assertions._assert(regionLiveBytes.get(new Integer(regionOf(object).toInt())) != 0);

                // object is not being forwarded, copy it
                ObjectReference newObject = VM.objectModel.copy(object, allocator);
                ForwardingWord.setForwardingPointer(object, newObject);
                trace.processNode(newObject);

                // TODO per region lock?
                int newLiveBytes = regionLiveBytes.get(new Integer(regionOf(object).toInt())) - sizeOf(object);
                regionLiveBytes.put(new Integer(regionOf(object).toInt()), newLiveBytes);
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
            Word forwardingWord = ForwardingWord.attemptToForward(object);
            if (!ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
                trace.processNode(object);
            }
        }
        // object is not in the collection set
        return object;
    }
}
