package org.mmtk.plan.younggen;

import org.mmtk.plan.Plan;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class YG extends StopTheWorld {

    public static boolean hi = false;

    public static final CopySpace eden = new CopySpace("eden", false, VMRequest.discontiguous());
    public static final int EDEN = eden.getDescriptor();

    public static final CopySpace cs0 = new CopySpace("cs0", false, VMRequest.discontiguous());
    public static final int CS0 = cs0.getDescriptor();

    public static final CopySpace cs1 = new CopySpace("cs1", false, VMRequest.discontiguous());
    public static final int CS1 = cs1.getDescriptor();

    public final Trace ygTrace;

    public static final int ALLOC_YG = Plan.ALLOC_DEFAULT;

    public static final int SCAN_YG = 0;

    public YG() {
        ygTrace = new Trace(metaDataSpace);
    }

    @Inline
    public static CopySpace fromSpace() {
        return hi ? cs0 : cs1;
    }

    @Inline
    public static CopySpace toSpace() {
        return hi ? cs1 : cs0;
    }

    @Override
    @Inline
    public final void collectionPhase(short phaseId) {
        if (phaseId == PREPARE) {
            hi = !hi;
            eden.prepare(true); // eden is always from space
            cs0.prepare(hi);
            cs1.prepare(!hi);
            ygTrace.prepare();
        }
        if (phaseId == CLOSURE) {
            ygTrace.prepare();
            return;
        }
        if (phaseId == PREPARE) {
            fromSpace().release();
            eden.release();
        }
        super.collectionPhase(phaseId);
    }

    /**********************************************
     * 
     *  Accounting
     */
    /**
     * Return the number of pages reserved for copying.
     */
    @Override
    public final int getCollectionReserve() {
        // we must account for the number of pages required for copying,
        // which equals the number of semi-space pages reserved
        return eden.reservedPages() + super.getCollectionReserve();
    }

    /**
     * Return the number of pages reserved for use given the pending allocation.
     * This is <i>exclusive of</i> space reserved for copying.
     */
    @Override
    public int getPagesUsed() {
        return super.getPagesUsed() + eden.reservedPages();
    }

    /**
     * Return the number of pages available for allocation, <i>assuming all future
     * allocation is to the semi-space</i>.
     *
     * @return The number of pages available for allocation, <i>assuming all future
     *         allocation is to the semi-space</i>.
     */
    @Override
    public final int getPagesAvail() {
        return (super.getPagesAvail()) >> 1;
    }

    @Override
    public boolean willNeverMove(ObjectReference object) {
        if (Space.isInSpace(CS0, object) || Space.isInSpace(CS1, object))
            return false;
        return super.willNeverMove(object);
    }

    @Override
    @Interruptible
    protected void registerSpecializedMethods() {
        TransitiveClosure.registerSpecializedScan(SCAN_YG, YGTraceLocal.class);
        super.registerSpecializedMethods();
    }
}
