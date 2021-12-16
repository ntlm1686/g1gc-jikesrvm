/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.region;

import org.mmtk.plan.*;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RegionSpace;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.VMRequest;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Extent;

/**
 * This class implements the global state of a a simple allocator
 * without a collector.
 */
@Uninterruptible
public class Region extends StopTheWorld {
  private static final int NET_META_DATA_BYTES_PER_REGION = 0;
  protected static final int META_DATA_PAGES_PER_REGION_WITH_BITMAP = Conversions
      .bytesToPages(Extent.fromIntSignExtend(NET_META_DATA_BYTES_PER_REGION));

  /*****************************************************************************
   * Class variables
   */

  /**
   *
   */
  public final static RegionSpace regionSpace = new RegionSpace("region", VMRequest.discontiguous());
  public static final int RS = regionSpace.getDescriptor();

  // * For debug
  public final static ImmortalSpace imospace = new ImmortalSpace("immortal", VMRequest.discontiguous());
  public static final int IM = imospace.getDescriptor();

  /*****************************************************************************
   * Instance variables
   */

  public final Trace regionTrace;
  public final Trace evaTrace;

  public static final int ALLOC_RS = Plan.ALLOC_DEFAULT;
  public static final int SCAN_RS = 0;
  public static final int EVA_RS = 1;

  /**
   * Constructor
   */
  public Region() {
    regionTrace = new Trace(metaDataSpace);
    evaTrace = new Trace(metaDataSpace);
  }

  /*****************************************************************************
   * Collection
   */

  @Inline
  @Override
  public final void collectionPhase(short phaseId) {
    if (phaseId == PREPARE) {
      Log.writeln("<GC> PREPARE global");
      regionSpace.prepare();
      regionTrace.prepare();
      // evaTrace.prepare();
      super.collectionPhase(phaseId);
      return;
    }
    if (phaseId == CLOSURE) {
      Log.writeln("<GC> CLOSURE global");
      regionTrace.prepare();
      return;
    }

    if (phaseId == UPDATE_COLLECTION_SET) {
      Log.writeln("<GC> UPDATE_COLLECITON_SET global");
      return;
    }

    if (phaseId == RELEASE) {
    //   regionSpace.release();
      regionSpace.updateCollectionSet();
      regionSpace.release();
      regionSpace.debug_info();
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   * Accounting
   */

  /**
   * {@inheritDoc}
   * The superclass accounts for its spaces, we just
   * augment this with the default space's contribution.
   */
  @Override
  public int getPagesUsed() {
    return (regionSpace.reservedPages() + super.getPagesUsed());
  }

  /*****************************************************************************
   * Miscellaneous
   */

  @Interruptible
  @Override
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_RS, RegionTraceLocal.class);
    TransitiveClosure.registerSpecializedScan(EVA_RS, RegionEvacuateLocal.class);
    super.registerSpecializedMethods();
  }
}
