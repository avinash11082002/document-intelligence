package com.docint.common.enums;

/**
 * Document lifecycle states.
 *
 * State machine transitions:
 *   UPLOADED → PROCESSING → EXTRACTING → CHUNKING → EMBEDDING → INDEXING → READY (terminal success)
 *   Any state → FAILED (terminal failure after retries exhausted)
 *   FAILED    → RETRYING → PROCESSING (admin replay or manual retry)
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    EXTRACTING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    READY,
    FAILED,
    RETRYING;

    /** True once processing has permanently succeeded or permanently failed. */
    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }

    /** True while the worker is actively doing something. */
    public boolean isInProgress() {
        return this == PROCESSING || this == EXTRACTING
                || this == CHUNKING || this == EMBEDDING
                || this == INDEXING || this == RETRYING;
    }
}
