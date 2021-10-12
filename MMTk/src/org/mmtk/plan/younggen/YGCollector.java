package org.mmtk.plan.semi;

import org.mmtk.plan.StopTheWorldCollector;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class SSCollector extends StopTheWorldCollector{

    @Override
    @Inline
    public final void collectionPhase(short phaseId, boolean primary) {
        if (phaseId == SS.PREPARE) {
        }
        if (phaseId == SS.CLOSURE) {
        }
        if (phaseId == SS.PREPARE) {
        }
        super.collectionPhase(phaseId, primary);
    }
}
