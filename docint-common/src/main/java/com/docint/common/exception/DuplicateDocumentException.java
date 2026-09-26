package com.docint.common.exception;

import java.util.UUID;

public class DuplicateDocumentException extends RuntimeException {
    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId, String contentHash) {
        super("Document with content hash " + contentHash + " already exists: " + existingDocumentId);
        this.existingDocumentId = existingDocumentId;
    }

    public UUID getExistingDocumentId() {
        return existingDocumentId;
    }
}
