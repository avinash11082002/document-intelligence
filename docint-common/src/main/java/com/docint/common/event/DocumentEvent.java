package com.docint.common.event;

import com.docint.common.enums.EventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event message envelope published over Kafka topics:
 * - document.uploaded
 * - document.indexed
 * - document.processing.dlq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private EventType eventType;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String correlationId;

    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private UUID documentId;
        private String ownerId;
        private String filename;
        private String contentType;
        private Long fileSizeBytes;
        private String contentHash;
        private String filePath;

        // DLQ / Error Context
        private String originalTopic;
        private Integer retryCount;
        private String lastError;
        private Instant lastErrorTimestamp;
    }
}
