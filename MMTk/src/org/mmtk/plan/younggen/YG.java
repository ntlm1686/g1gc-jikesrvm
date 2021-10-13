package org.mmtk.plan.younggen;

import org.mmtk.plan.Plan;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class YG extends StopTheWorld {

    public static boolean hi = false;

    public static final CopySpace eden = new CopySpace("young", false, VMRequest.discontiguous());
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
            eden.prepare(true);   // eden is always from space
            cs0.prepare(hi);
            cs1.prepare(!hi);
            ygTrace.prepare();
        }
        if (phaseId == CLOSURE) {
            ygTrace.prepare();
            return;
        }
        if (phaseId == PREPARE) {
            eden.release();
            fromSpace().release();
            eden.release();
        }
        super.collectionPhase(phaseId);
    }

    /**********************************************
     * 
     * TODO: Accounting
     */

}
