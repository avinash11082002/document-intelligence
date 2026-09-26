package com.docint.common.dto.conversation;

import java.util.UUID;

public record CitationResponse(
        UUID documentId,
        String filename,
        String chunkId,
        Integer pageNumber,
        String snippet,
        Double score
) {}
