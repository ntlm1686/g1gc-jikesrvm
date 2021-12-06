package org.mmtk.plan.region;

import org.mmtk.plan.TraceLocal;

import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public class RegionEvacuateLocal extends TraceLocal {

    public RegionEvacuateLocal(Trace trace) {
        super(Region.EVA_RS, trace);
    }

    /****************************************************************************
     *
     * Externally visible Object processing and tracing
     */
    @Override
    public boolean isLive(ObjectReference object) {
        if (object.isNull())
            return false;
        if (Space.isInSpace(Region.RS, object)) {
            return Region.regionSpace.isLive(object);
        }
        return super.isLive(object);
    }

    @Inline
    @Override
    public ObjectReference traceObject(ObjectReference object) {
        if (object.isNull())
            return object;
        if (Space.isInSpace(Region.RS, object))
            return Region.regionSpace.traceEvacuateObject(this, object, Region.ALLOC_RS);
        return super.traceObject(object);
    }

    @Override
    public boolean willNotMoveInCurrentCollection(ObjectReference object) {
        if (Space.isInSpace(Region.RS, object))
            return Region.regionSpace.relocationRequired(object);
        return super.willNotMoveInCurrentCollection(object);
    }
}
