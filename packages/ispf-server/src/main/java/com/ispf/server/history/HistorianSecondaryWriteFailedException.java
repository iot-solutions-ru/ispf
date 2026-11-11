package com.ispf.server.history;

/**
 * Historian primary accepted the write but a best-effort secondary store failed (ADR-0035).
 * Batched writers must not fan out per-sample retries for this failure mode.
 */
public final class HistorianSecondaryWriteFailedException extends RuntimeException {

    public HistorianSecondaryWriteFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
