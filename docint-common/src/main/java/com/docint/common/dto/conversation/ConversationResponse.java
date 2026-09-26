package com.docint.common.dto.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID documentId,
        String documentName,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {}
