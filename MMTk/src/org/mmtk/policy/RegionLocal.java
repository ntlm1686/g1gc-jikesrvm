package org.mmtk.policy;

import org.mmtk.utility.alloc.RegionAllocator;
import org.vmmagic.pragma.*;

@Uninterruptible
public final class RegionLocal extends RegionAllocator {

    protected RegionLocal(RegionSpace space, int allocationKind, boolean allowScanning) {
        super(space, allocationKind, allowScanning);
        //TODO Auto-generated constructor stub
    }

}
