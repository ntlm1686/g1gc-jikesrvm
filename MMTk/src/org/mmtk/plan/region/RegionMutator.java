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

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.RegionLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>NoGC</i> plan, which simply allocates (without ever collecting until the
 * available space is exhausted.
 * <p>
 *
 * Specifically, this class defines <i>NoGC</i> mutator-time allocation through
 * a bump pointer (<code>def</code>) and includes stubs for per-mutator thread
 * collection semantics (since there is no collection in this plan, these remain
 * just stubs).
 *
 * @see Region
 * @see RegionCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class RegionMutator extends StopTheWorldMutator {

  /************************************************************************
   * Instance fields
   */

  /**
   *
   */
  protected final RegionLocal rl;
  protected final ImmortalLocal il;

  public RegionMutator() {
    rl = new RegionLocal(Region.regionSpace);
    il = new ImmortalLocal(Region.imospace);
  }

  /****************************************************************************
   * Mutator-time allocation
   */

  /**
   * {@inheritDoc}
   */
  @Inline
  @Override
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == Region.ALLOC_DEFAULT) {
      return rl.alloc(bytes, align, offset);
      // return il.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  @Inline
  @Override
  public void postAlloc(ObjectReference ref, ObjectReference typeRef, int bytes, int allocator) {
    if (allocator == Region.ALLOC_DEFAULT)
      Region.regionSpace.postAlloc(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == Region.regionSpace)
      return rl;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Region.PREPARE) {
      super.collectionPhase(phaseId, primary);
      rl.reset(); // return the region to the global pool, since it might be evacuated later.
      return;
    }

    // if (phaseId == Region.RELEASE) {
    //   ;// do nothing
    // }
    // rl.reset();
    super.collectionPhase(phaseId, primary);
  }
}
