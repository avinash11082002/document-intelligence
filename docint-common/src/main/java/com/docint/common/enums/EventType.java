package com.docint.common.enums;

/**
 * Kafka event types for asynchronous pipeline communication.
 */
public enum EventType {
    DOCUMENT_UPLOADED,
    DOCUMENT_INDEXED,
    PROCESSING_FAILED
}
