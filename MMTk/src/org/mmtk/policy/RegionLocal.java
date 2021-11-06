package org.mmtk.policy;

import org.mmtk.utility.alloc.RegionAllocator;
import org.vmmagic.pragma.*;

@Uninterruptible
public final class RegionLocal extends RegionAllocator {

    protected RegionLocal(RegionSpace space) {
        super(space);
        //TODO Auto-generated constructor stub
    }

}
