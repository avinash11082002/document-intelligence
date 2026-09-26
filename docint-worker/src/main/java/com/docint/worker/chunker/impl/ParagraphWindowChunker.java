package com.docint.worker.chunker.impl;

import com.docint.worker.chunker.TextChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ParagraphWindowChunker implements TextChunker {

    @Override
    public List<Chunk> chunk(String text, int chunkSizeTokens, double overlapRatio) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSizeChars = chunkSizeTokens * 4;
        int overlapChars = (int) (chunkSizeChars * overlapRatio);
        int stepSize = Math.max(1, chunkSizeChars - overlapChars);

        List<Chunk> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();

        int index = 0;
        int offset = 0;

        while (offset < normalized.length()) {
            int end = Math.min(offset + chunkSizeChars, normalized.length());
            String chunkText = normalized.substring(offset, end);

            // Natural boundary detection
            if (end < normalized.length()) {
                int lastDoubleNewline = chunkText.lastIndexOf("\n\n");
                int lastPeriod = chunkText.lastIndexOf(". ");
                int lastNewline = chunkText.lastIndexOf('\n');

                int breakPoint = Math.max(lastDoubleNewline, Math.max(lastPeriod, lastNewline));
                if (breakPoint > chunkSizeChars / 2) {
                    chunkText = chunkText.substring(0, breakPoint + 1).trim();
                }
            }

            if (!chunkText.isBlank()) {
                chunks.add(Chunk.builder()
                        .index(index++)
                        .content(chunkText.trim())
                        .charOffset(offset)
                        .build());
            }

            offset += stepSize;
            if (offset >= normalized.length()) break;
        }

        log.info("Split text into {} chunks (size={} tokens, overlap={}%)",
                chunks.size(), chunkSizeTokens, (int)(overlapRatio * 100));
        return chunks;
    }
}
