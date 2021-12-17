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
import org.mmtk.policy.RegionLocal;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>NoGC</i> plan, which simply allocates (without ever collecting until the
 * available space is exhausted.
 * <p>
 *
 * Specifically, this class <i>would</i> define <i>NoGC</i> collection time
 * semantics, however, since this plan never collects, this class consists only
 * of stubs which may be useful as a template for implementing a basic
 * collector.
 *
 * @see Region
 * @see RegionMutator
 * @see CollectorContext
 */
@Uninterruptible
public class RegionCollector extends StopTheWorldCollector {
  /************************************************************************
   * Instance fields
   */

  private final RegionTraceLocal trace; // = new RegionTraceLocal(global().regionTrace);
  private final RegionEvacuateLocal eva;
  protected final RegionLocal rl;

  /**
   * Constructor
   */
  public RegionCollector() {
    this(
      new RegionTraceLocal(global().regionTrace),
      new RegionEvacuateLocal(global().evaTrace)
      );
  }

  public RegionCollector(RegionTraceLocal trace, RegionEvacuateLocal eva) {
    this.eva = eva;
    this.trace = trace;
    this.rl = new RegionLocal(Region.regionSpace);
  }

  /****************************************************************************
   * Collection
   */

  /**
   * Perform a garbage collection
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Region.PREPARE) {
      super.collectionPhase(phaseId, primary);
      rl.reset();
      return;
    }

    if (phaseId == Region.CLOSURE) {
      Log.writeln("<GC> CLOSURE collector");
      trace.completeTrace();
      return;
    }

    if (phaseId == Region.EVACUATE) {
      Log.writeln("<GC> EVACUATE collector");
      // eva.completeTrace();
      return;
    }

    // if (phaseId == Region.RELEASE) {
    //   trace.release();
    //   eva.release();
    // }
    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Collection-time allocation
   */
  @Override
  @Inline
  public Address allocCopy(ObjectReference original, int bytes, int align, int offset, int allocator) {
      return rl.alloc(bytes, align, offset);
  }

  @Override
  @Inline
  public void postCopy(ObjectReference object, ObjectReference typeRef, int bytes, int allocator) {
    ForwardingWord.clearForwardingBits(object);
    Region.regionSpace.postCopy(object);
  }

  /****************************************************************************
   * Miscellaneous
   */

  /** @return The active global plan as a <code>NoGC</code> instance. */
  @Inline
  private static Region global() {
    return (Region) VM.activePlan.global();
  }

  @Override
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
}
