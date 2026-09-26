package com.docint.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for asking a question against the RAG pipeline.
 * ownerId is intentionally absent — it is derived from the authenticated JWT.
 */
public record QueryRequest(
        /** Optional: scope retrieval to a specific document. Null = workspace-wide. */
        UUID documentId,

        /** Optional: associate this query with an existing conversation. */
        UUID conversationId,

        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must not exceed 2000 characters")
        String question
) {}
