package org.mmtk.plan.younggen;

import org.mmtk.plan.Trace;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class YGTraceLocal extends TraceLocal {

    public YGTraceLocal(Trace trace, boolean specialized) {
        super(specialized ? YG.SCAN_YG : -1, trace);
    }

    public YGTraceLocal(Trace trace) {
        this(trace, true);
    }

    @Override
    public boolean isLive(ObjectReference object) {
        if (object.isNull())
            return false;
        if (Space.isInSpace(YG.CS0, object))
        //                            From Space : To Space
            return YG.hi ? YG.cs0.isLive(object) : true;
        if (Space.isInSpace(YG.CS1, object))
            return YG.hi ? true : YG.cs1.isLive(object);
        if (Space.isInSpace(YG.EDEN, object))
            return YG.eden.isLive(object);
        return super.isLive(object);
    }

    @Override
    @Inline
    public ObjectReference traceObject(ObjectReference object) {
        if (object.isNull())
            return object;
        // * traceObject here also copy the object to to-space
        if (Space.isInSpace(YG.CS1, object))
            return YG.cs0.traceObject(this, object, YG.ALLOC_YG);
        if (Space.isInSpace(YG.CS1, object))
            return YG.cs1.traceObject(this, object, YG.ALLOC_YG);
        if (Space.isInSpace(YG.EDEN, object))
            return YG.eden.traceObject(this, object, YG.ALLOC_YG);
        return super.traceObject(object);
    }

    /**
     * Will this object move from this point on, during the current trace ?
     * (Object which is not in Eden space nor From space)
     *
     * @param object The object to query.
     * @return True if the object will not move.
     */
    @Override
    public boolean willNotMoveInCurrentCollection(ObjectReference object) {
        if (Space.isInSpace(YG.EDEN, object))
            return false;
        return (YG.hi && !Space.isInSpace(YG.CS0, object)) ||
               (!YG.hi && !Space.isInSpace(YG.CS1, object));
    }
}