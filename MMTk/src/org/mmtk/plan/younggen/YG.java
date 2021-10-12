package org.mmtk.plan.younggen;

import org.mmtk.plan.StopTheWorld;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class YG extends StopTheWorld {

    static boolean cs = false;

    public static final CopySpace eden = new CopySpace("young", false, VMRequest.discontiguous());
    public static final CopySpace cs0 = new CopySpace("cs0", false, VMRequest.discontiguous());
    public static final CopySpace cs1 = new CopySpace("cs1", false, VMRequest.discontiguous());

    public static final int ALLOC_SS = 0;

    @Inline
    public static CopySpace fromSpace() {
        return cs ? cs0 : cs1;
    }

    @Inline
    public static CopySpace toSpace() {
        return cs ? cs1 : cs0;
    }


    @Override
    @Inline
    public final void collectionPhase(short phaseId) {
        if (phaseId == PREPARE) {
        }
        if (phaseId == CLOSURE) {
        }
        if (phaseId == PREPARE) {
        }
        super.collectionPhase(phaseId);
    }

}
