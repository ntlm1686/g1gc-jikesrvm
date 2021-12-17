package org.mmtk.policy;

import java.util.*;

import static org.mmtk.utility.Constants.BYTES_IN_PAGE;
import static org.mmtk.utility.alloc.RegionAllocator.DATA_END_OFFSET;
import static org.mmtk.utility.alloc.RegionAllocator.DATA_START_OFFSET;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible public final class RegionSpace extends Space {

    // Constraints
    public static final int LOCAL_GC_BITS_REQUIRED = 2;
    public static final int GC_HEADER_WORDS_REQUIRED = 0;
    public static final boolean HEADER_MARK_BITS = VM.config.HEADER_MARK_BITS;
    // highest bit bits we may use
    private static final int AVAILABLE_LOCAL_BITS = 8 - HeaderByte.USED_GLOBAL_BITS;
    private static final int COUNT_BASE = 0;

    // Mark bit
    public static final int DEFAULT_MARKCOUNT_BITS = 4;
    public static final int MAX_MARKCOUNT_BITS = AVAILABLE_LOCAL_BITS - COUNT_BASE;
    private static final byte MARK_COUNT_INCREMENT = (byte) (1 << COUNT_BASE);
    private static final byte MARK_COUNT_MASK = (byte) (((1 << MAX_MARKCOUNT_BITS) - 1) << COUNT_BASE); // minus 1 for

    // Mark bit state
    private byte markState = 1;
    private byte allocState = 0;

    // Region info
    private static final int META_DATA_PAGES_PER_REGION = 0;
    public static final int PAGES_PER_REGION = 512;
    // public static final int PAGES_PER_REGION = 256;
    public static final int REGION_SIZE = BYTES_IN_PAGE * PAGES_PER_REGION;
    public static final Extent REGION_EXTENT = Word.fromIntZeroExtend(REGION_SIZE).toExtent();
    public static final int REGION_NUMBER = 1000;

    // public static final Extent REGION_SIZE = Word.fromIntZeroExtend(1024).toExtent();
    // public static final int PAGE_NUMBER = Conversions.bytesToPages(REGION_SIZE);


    protected final Lock lock = VM.newLock("RegionSpaceGloabl");
    // TODO replace with shared queue?
    protected final AddressArray regionTable = AddressArray.create(REGION_NUMBER);
    public final AddressArray regionDataEnd = AddressArray.create(REGION_NUMBER);

    private final int[] availableRegion = new int[REGION_NUMBER];

    int availableRegionCount = 0;
    int regionCount = 0;

    // Regions on which garbage collector will be executed
    private final int[] collectionSet = new int[REGION_NUMBER];
    private int collectionSetSize = 0;

    // Regions info
    private final int[] regionLiveBytes = new int[REGION_NUMBER];
    private final boolean[] requireRelocation  = new boolean[REGION_NUMBER];

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
    }

    /**
     * Release allocated pages.
     */
    public void release(Address start) {
        ((FreeListPageResource) pr).releasePages(start);
    }

    /**
     * Release pages
     */
    public void release() {
        // for (Integer regionAddress : collectionSet) {
        //     release(Address.fromLong(new Long(regionAddress)));
        //     availableRegionCount++;
        //     assert availableRegionCount < REGION_NUMBER;
        //     availableRegion.set(availableRegionCount, Address.fromLong(new Long(regionAddress)));
        // }

        // flip mark bit
        byte tmp = allocState;
        allocState = markState;
        markState = tmp;

        // markState = deltaMarkState(true);
        Log.writeln("[prepare] allocState: ", allocState);
        Log.writeln("[prepare] markState: ", markState);
    }

    @Override
    @Inline
    public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
        VM.assertions.fail("CopySpace.traceLocal called without allocator");
        return ObjectReference.nullReference();
    }

    /**
     * If an object is alive.
     */
    @Override
    @Inline
    public boolean isLive(ObjectReference object) {
        return testMarkState(object);
    }

    public Address regionFromIx(int ix) {
        return regionTable.get(ix);
    }

    /**
     * Prepare for the next GC.
     */
    public void prepare() {
        // flip the mark bit
        Log.writeln("[prepare] enter");
        // reset the regions' info

        this.resetRegionLiveBytes();
        this.resetRequireRelocation();

        // collectionSet.clear();
        collectionSetSize = 0;
    }

    /**
     * Give a new region to the allocator.
     *
     * @return -1 if no avaliable region, else the index of that region in RegionTable
     */
    @Inline
    @Interruptible
    public int getRegion() {
        Log.writeln("[getRegion] enter");

        lock.acquire();
        if (availableRegionCount == 0) {
            lock.release();

            Log.writeln("[getRegion] pool has no available region, try to acquire pages");
            Address newRegion = acquire(PAGES_PER_REGION);
            Log.writeln("[getRegion] new region: ", newRegion.toInt());
            if (newRegion.EQ(Address.zero())) {
                Log.writeln("[getRegion] acquire failed, exit");
                return -1;
            } else {
                Log.writeln("[getRegion] acquire success, exit");
            }
                
            lock.acquire();
            regionTable.set(regionCount, newRegion);
            regionCount++;
            lock.release();

            return regionCount - 1;
        }

        Log.writeln("[getRegion] pool has available regions, exit");
        int newRegion = availableRegion[availableRegionCount];
        availableRegionCount--;
        lock.release();

        VM.assertions._assert(availableRegionCount >= 0);
        return newRegion;
    }

    public void setDataEnd(int ix, Address dataEnd) {
        regionDataEnd.set(ix, dataEnd);
    }

    /**
     * Initialize the regions.
     */
    @Inline
    private void initializeRegions() {
        for (int i = 0; i < REGION_NUMBER; i++) {
            // availableRegionCount++;
            regionTable.set(i, Address.zero());
        }
    }

    private boolean isRegionIdeal(Address X, Address Y) {
        return (X.toInt() < Y.toInt()) && (X.toInt() + REGION_SIZE >= Y.toInt());
    }

    private int idealRegion(Address address) {
        for (int i = 0; i < REGION_NUMBER; i++) {
            if (isRegionIdeal(regionTable.get(i), address)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Return the region this object belongs to.
     *
     * @param object
     * @return
     */
    @Inline
    public int regionOf(ObjectReference object) {
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
            int region = regionOf(object);
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
     * Collection set
     *
     * @return
     */
    public void updateCollectionSet() {
        Log.writeln("[updateCollectionSet] enter");
        // calculate dead Bytes from lives
        // updateDeadBytes();

        // int max_collection_set_size = REGION_NUMBER - regionCount;
        // For testing purpose, we currently only choose half of the regions to evacuate
        collectionSetSize = (int)(0.5 * regionCount);

        int[] sorted = sortTableByLiveBytes();
        // sort regionTable by dead bytes

        // derive the collection set
        for (int i=0; i<collectionSetSize; i++) {
            collectionSet[i] = sorted[i];
            requireRelocation[sorted[i]] = true;
        }
    }

    // ascending order
    public int[] sortTableByLiveBytes() {
        int[] sorted = new int[regionCount];

        // initiliaze
        for (int i=0; i<regionCount; i++)
            sorted[i] = i;
        
        // sort
        for (int i=0; i<regionCount; i++) {
            for (int j=1; j<regionCount-1; j++) {
                if (regionLiveBytes[sorted[j-1]] > regionLiveBytes[sorted[j]]) {
                    int tmp;
                    tmp = sorted[j-1];
                    sorted[j-1] = sorted[j];
                    sorted[j] = tmp;
                }
            }
        }

        return sorted;
    }

    // /**
    //  * Update dead bytes of each region by using (dataEnd-dataStart) - liveBytes
    //  */
    // public void updateDeadBytes() {
    //     for (int i = 0; i < regionCount; i++) {
    //         // regionDataEnd - (regionStart + DATA_START_OFFSET) - regionLiveBytes
    //         regionDeadBytes[i] = REGION_SIZE - (regionDataEnd.get(i).diff(
    //             regionTable.get(i).plus(DATA_START_OFFSET)
    //             ).toInt() - regionLiveBytes[i]);
    //     }
    // }

    public static Address getDataEnd(Address region) {
        return region.plus(DATA_END_OFFSET).loadAddress();
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
        // byte newValue = (byte) ((oldValue & ~MARK_COUNT_MASK) | (alloc && !isAllocAsMarked ? allocState : markState));
        byte newValue = (byte) ((oldValue & ~MARK_COUNT_MASK) | allocState);

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
        return Boolean.TRUE.equals(requireRelocation[regionOf(object)]);
    }

    /**
     * Set all regions' live bytes to 0.
     */
    @Inline
    private void resetRegionLiveBytes() {
        // assert regionTable has been initialized
        for (int i = 0; i < regionCount; i++) {
            regionLiveBytes[i] = 0;
        }
    }

    /**
     * All regions do not require relocation.
     */
    @Inline
    private void resetRequireRelocation() {
        // assert regionTable has been initialized
        for (int i = 0; i < regionCount; i++) {
            requireRelocation[i] = false;
        }
    }

    /**
     * Update the live bytes of a region by adding the size of the object.
     *
     * @param region
     * @param object
     */
    @Inline
    private void updateRegionliveBytes(int region, ObjectReference object) {
        // update the region's live bytes
        // Log.writeln("[updateRegionliveBytes] size = ", regionLiveBytes[region]);
        // Log.writeln("[updateRegionliveBytes] + ", sizeOf(object));
        regionLiveBytes[region] += sizeOf(object);
    }

    /**
     * Return the size of an object.
     */
    @Inline
    private int sizeOf(ObjectReference object) {
        Address objectStart = object.toAddress();
        Address objectEnd = VM.objectModel.getObjectEndAddress(object);
        Offset size = objectEnd.diff(objectStart);
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
    //     if (relocationRequired(object)) {
    //         Word forwardingWord = ForwardingWord.attemptToForward(object);
    //         if (ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
    //             while (ForwardingWord.stateIsBeingForwarded(forwardingWord))
    //                 forwardingWord = VM.objectModel.readAvailableBitsWord(object);
    //             return ForwardingWord.extractForwardingPointer(forwardingWord);
    //         } else {
    //             if (VM.VERIFY_ASSERTIONS)
    //                 VM.assertions._assert(regionLiveBytes.get(new Integer(regionOf(object).toInt())) != 0);

    //             // object is not being forwarded, copy it
    //             ObjectReference newObject = VM.objectModel.copy(object, allocator);
    //             ForwardingWord.setForwardingPointer(object, newObject);
    //             trace.processNode(newObject);

    //             // TODO per region lock?
    //             int newLiveBytes = regionLiveBytes.get(new Integer(regionOf(object).toInt())) - sizeOf(object);
    //             regionLiveBytes.put(new Integer(regionOf(object).toInt()), newLiveBytes);
    //             if (newLiveBytes == 0) {
    //                 // if new live bytes is 0, the region is empty, it's available again
    //                 lock.acquire();
    //                 availableRegionCount++;
    //                 availableRegion.set(availableRegionCount, regionOf(object));
    //                 lock.release();
    //             }
    //             return newObject;
    //         }
    //     } else {
    //         Word forwardingWord = ForwardingWord.attemptToForward(object);
    //         if (!ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
    //             trace.processNode(object);
    //         }
    //     }
    //     // object is not in the collection set
        return object;
    }

    public void debug_info() {
        Log.writeln("************ ARRAY INFO ************");
        Log.writeln("Total region: ", regionCount);

        for (int i=0; i<regionCount; i++) {
            Log.write("region: ", i);
            Log.write(", live bytes: ", regionLiveBytes[i]);
            int isRequired = requireRelocation[i] ? 1 : 0;
            Log.write(", require relocation: ", isRequired);
            Log.writeln();
        }

        Log.writeln("************    END     ************");
    }
}
