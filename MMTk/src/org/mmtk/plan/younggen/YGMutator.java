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

    protected final CopyLocal yg;

    // CopyLocal which used to allocate objects in copy space
    public YGMutator() {
        yg = new CopyLocal();
    }

    @Override
    public void initMutator(int id) {
        super.initMutator(id); // register mutator thread
        yg.rebind(YG.eden);
    }

    // provide alloc methods for object allocation, and postAlloc for assitance
    @Override
    @Inline
    public Address alloc(int bytes, int align, int offset, int allocator, int site) {
        if (allocator == YG.ALLOC_YG)
            return yg.alloc(bytes, align, offset);
        else
            return super.alloc(bytes, align, offset, allocator, site);
    }

    @Override
    @Inline
    public void postAlloc(ObjectReference object, ObjectReference typeRef, int bytes, int allocator) {
        if (allocator == YG.ALLOC_YG)
            return;
        super.postAlloc(object, typeRef, bytes, allocator);
    }

    @Override
    public Allocator getAllocatorFromSpace(Space space) {
        if (space == YG.cs0 || space == YG.cs1 || space == YG.eden)
            return yg;
        return super.getAllocatorFromSpace(space);
    }

    @Override
    @Inline
    public void collectionPhase(short phaseId, boolean primary) {
        if (phaseId == YG.PREPARE) {
            super.collectionPhase(phaseId, primary);
            return;
        }

        if (phaseId == YG.RELEASE) {
            super.collectionPhase(phaseId, primary);
            // swap from and to space
            yg.rebind(YG.eden);
            return;
        }
        super.collectionPhase(phaseId, primary);
    }

    public final void show() {
        yg.show();
        los.show();
        immortal.show();
    }

}
