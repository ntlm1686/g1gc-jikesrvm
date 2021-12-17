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

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class RegionTraceLocal extends TraceLocal {

  /**
   * @param trace the associated global trace
   */
  public RegionTraceLocal(Trace trace) {
    super(Region.SCAN_RS, trace);
  }

  /****************************************************************************
   * Externally visible Object processing and tracing
   */

  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(Region.RS, object)) {
      return true;
      // return Region.regionSpace.isLive(object);
    }
    return super.isLive(object);
  }

  @Inline
  @Override
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) {
      return object;
    }
    if (Space.isInSpace(Region.RS, object))
      return Region.regionSpace.traceObject(this, object, Region.ALLOC_DEFAULT);
    return super.traceObject(object);
  }
}
