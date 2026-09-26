package com.docint.common.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadResponse(
        UUID id,
        String filename,
        String contentType,
        Long fileSizeBytes,
        String contentHash,
        String status,
        Instant createdAt,
        /** Present when a duplicate is detected — points to the existing document. */
        UUID existingDocumentId,
        String message
) {
    /** Constructor for a successful new upload. */
    public static DocumentUploadResponse uploaded(UUID id, String filename, String contentType,
                                                   Long fileSizeBytes, String contentHash,
                                                   String status, Instant createdAt) {
        return new DocumentUploadResponse(id, filename, contentType, fileSizeBytes,
                contentHash, status, createdAt, null,
                "Document uploaded successfully and queued for processing.");
    }

    /** Constructor for duplicate detection response. */
    public static DocumentUploadResponse duplicate(UUID existingId, String filename,
                                                    String contentHash, Instant createdAt) {
        return new DocumentUploadResponse(existingId, filename, null, null,
                contentHash, "READY", createdAt, existingId,
                "This document already exists in your workspace. The existing document is returned.");
    }
}
