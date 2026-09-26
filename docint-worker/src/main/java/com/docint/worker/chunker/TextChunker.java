package com.docint.worker.chunker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Text chunking strategy interface.
 */
public interface TextChunker {

    List<Chunk> chunk(String text, int chunkSizeTokens, double overlapRatio);

    default List<Chunk> chunk(String text) {
        return chunk(text, 512, 0.10);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class Chunk {
        private int index;
        private String content;
        private int charOffset;
        private Integer pageNumber;
    }
}
