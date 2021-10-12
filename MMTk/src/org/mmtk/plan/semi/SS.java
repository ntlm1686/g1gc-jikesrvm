package org.mmtk.plan.semi;

import org.mmtk.plan.StopTheWorld;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class SS extends StopTheWorld {
    public static final CopySpace cs0 = new CopySpace("cs0", false, VMRequest.discontiguous());
    public static final CopySpace cs1 = new CopySpace("cs1", false, VMRequest.discontiguous());

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
