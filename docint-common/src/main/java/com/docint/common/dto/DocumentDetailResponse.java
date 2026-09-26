package com.docint.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID id,
        String filename,
        String contentType,
        Long fileSizeBytes,
        String contentHash,
        String status,
        String documentType,
        String summary,
        Integer pageCount,
        List<String> suggestedQuestions,
        Map<String, Object> extractedFields,
        Integer chunkCount,
        String failureReason,
        Integer retryCount,
        Instant createdAt,
        Instant processedAt,
        Instant updatedAt
) {}
