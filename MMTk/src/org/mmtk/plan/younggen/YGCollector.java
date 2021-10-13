package org.mmtk.plan.younggen;

import org.mmtk.plan.*;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.ForwardingWord;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class YGCollector extends StopTheWorldCollector {

    protected final YGTraceLocal trace;
    protected final CopyLocal yg;
    protected final LargeObjectLocal los;

    public YGCollector() {
        this(new YGTraceLocal(global().ygTrace));
    }

    protected YGCollector(YGTraceLocal tr) {
        yg = new CopyLocal();
        los = new LargeObjectLocal(Plan.loSpace);
        trace = tr;
    }
    /*******************************************************
     * 
     * copy
     * * The methods defined here is used to copy objects from From space,
     * * Copy is performed during tracing objects.
     */

    @Override
    @Inline
    public Address allocCopy(ObjectReference original, int bytes, int align, int offset, int allocator) {
        if (allocator == Plan.ALLOC_LOS) {
            if (VM.VERIFY_ASSERTIONS)
                VM.assertions._assert(bytes > Plan.MAX_NON_LOS_COPY_BYTES);
            return los.alloc(bytes, align, offset);
        } else {
            if (VM.VERIFY_ASSERTIONS) {
                VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
                VM.assertions._assert(allocator == YG.ALLOC_YG);
            }
            return yg.alloc(bytes, align, offset);
        }
    }

    @Override
    @Inline
    public void postCopy(ObjectReference object, ObjectReference typeRef, int bytes, int allocator) {
        ForwardingWord.clearForwardingBits(object);
        if (allocator == Plan.ALLOC_LOS)
            Plan.loSpace.initializeHeader(object, false);
    }
    
    /*****************************************************
     * 
     * Collection phase
     */

    @Override
    @Inline
    public final void collectionPhase(short phaseId, boolean primary) {
        if (phaseId == YG.PREPARE) {
            yg.rebind(YG.toSpace());
            los.prepare(true);
        }
        if (phaseId == YG.CLOSURE) {
            trace.completeTrace();
            // why return here?
            return;
        }
        if (phaseId == YG.PREPARE) {
            trace.release();
            los.release(true);
        }
        super.collectionPhase(phaseId, primary);
    }

    /** Unknown */
    public static boolean isSemiSpaceObject(ObjectReference object) {
        return Space.isInSpace(YG.CS0, object) || Space.isInSpace(YG.CS1, object) || Space.isInSpace(YG.EDEN, object);
    }

    @Inline
    private static YG global() {
        return (YG) VM.activePlan.global();
    }

    @Override
    public TraceLocal getCurrentTrace() {
        return trace;
    }
}
