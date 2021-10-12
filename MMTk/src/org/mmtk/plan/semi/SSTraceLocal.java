package org.mmtk.plan.semi;

import org.mmtk.plan.Trace;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class SSTraceLocal extends TraceLocal {

     public SSTraceLocal(Trace trace, boolean specialized) {
        // super(specialized ? SS.SCAN_SS : -1, trace);
        super(-1, trace);
    }

    public SSTraceLocal(Trace trace) {
        this(trace, true);
    }
}
