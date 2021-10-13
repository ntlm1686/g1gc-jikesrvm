package org.mmtk.plan.younggen;

import org.mmtk.plan.StopTheWorldConstraints;
import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class YGConstraints extends StopTheWorldConstraints {

    @Override
    public boolean movesObjects() {
        return true;
    }

    @Override
    public int gcHeaderBits() {
        return CopySpace.LOCAL_GC_BITS_REQUIRED;
    }

    @Override
    public int gcHeaderWords() {
        return CopySpace.GC_HEADER_WORDS_REQUIRED;
    }

    @Override
    public int numSpecializedScans() {
        return 1;
    }
}
