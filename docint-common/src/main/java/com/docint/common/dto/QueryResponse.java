package com.docint.common.dto;

import java.util.List;

public record QueryResponse(
        String answer,
        List<SourceChunk> sourceChunks,
        boolean cached,
        String llmProvider,
        long latencyMs
) {}
