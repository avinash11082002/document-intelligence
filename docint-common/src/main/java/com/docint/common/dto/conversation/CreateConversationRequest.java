package com.docint.common.dto.conversation;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateConversationRequest(
        /** Optional: scope this conversation to a specific document. */
        UUID documentId,

        @Size(max = 512, message = "title must not exceed 512 characters")
        String title
) {}
