package org.mmtk.policy;

import org.mmtk.utility.alloc.RegionAllocator;
import org.vmmagic.pragma.*;

@Uninterruptible
public final class G1Local extends RegionAllocator {

    protected G1Local(RegionSpace space, boolean allowScanning) {
        super(space, allowScanning);
        //TODO Auto-generated constructor stub
    }
}
