package com.docint.common.dto.conversation;

import com.docint.common.enums.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        List<CitationResponse> citations,
        Long latencyMs,
        String provider,
        Instant createdAt
) {}
