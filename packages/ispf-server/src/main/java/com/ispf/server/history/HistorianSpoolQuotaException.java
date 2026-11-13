package com.ispf.server.history;

/**
 * The sole-store spool is full. Callers must not drop the sample and must not delete older segments.
 */
public class HistorianSpoolQuotaException extends IllegalStateException {

    public HistorianSpoolQuotaException(long usedBytes, long maxBytes) {
        super("historian spool quota exhausted (" + usedBytes + " of " + maxBytes + " bytes)");
    }
}
