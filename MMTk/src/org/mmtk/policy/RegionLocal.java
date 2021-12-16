package org.mmtk.policy;

import org.mmtk.utility.alloc.RegionAllocator;
import org.vmmagic.pragma.*;

@Uninterruptible public final class RegionLocal extends RegionAllocator {
    public RegionLocal(RegionSpace space) {
        super(space);
    }
}
