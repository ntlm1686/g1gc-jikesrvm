package org.mmtk.plan.younggen;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class YGMutator extends StopTheWorldMutator {

    protected final CopyLocal ss;

    // CopyLocal which used to allocate objects in copy space
    public YGMutator() {
        ss = new CopyLocal();
    }

    @Override
    public void initMutator(int id) {
        super.initMutator(id);      // register mutator thread
    }

    // provide alloc methods for object allocation, and postAlloc for assitance
    @Override
    @Inline
    public Address alloc(int bytes, int align, int offset, int allocator, int site) {
        if (allocator == YG.ALLOC_SS)
            return ss.alloc(bytes, align, offset);
        else
            return super.alloc(bytes, align, offset, allocator, site);
    }

    @Override
    @Inline
    public void postAlloc(ObjectReference object, ObjectReference typeRef, int bytes, int allocator) {
        if (allocator == YG.ALLOC_SS)
            return;
        super.postAlloc(object, typeRef, bytes, allocator);
    }

    @Override
    public Allocator getAllocatorFromSpace(Space space) {
        if (space == YG.cs0 || space == YG.cs1)
            return ss;
        return super.getAllocatorFromSpace(space);
    }

    @Override
    @Inline
    public void collectionPhase(short phaseId, boolean primary) {
        if (phaseId == YG.COMPLETE) {
            // swap from and to space
            ss.rebind(YG.toSpace());
        }
        super.collectionPhase(phaseId, primary);
    }

    
}
