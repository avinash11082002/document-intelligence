package com.docint.common.dto;

import java.util.UUID;

public record SourceChunk(
        String chunkId,
        UUID documentId,
        String filename,
        String snippet,
        String content,
        double score,
        Integer pageNumber
) {
    public SourceChunk(String chunkId, UUID documentId, String filename, String content, double score, Integer pageNumber) {
        this(
                chunkId,
                documentId,
                filename,
                generateSnippet(content),
                content,
                Math.round(score * 1000.0) / 1000.0,
                pageNumber
        );
    }

    private static String generateSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 200 ? cleaned : cleaned.substring(0, 197) + "...";
    }
}
