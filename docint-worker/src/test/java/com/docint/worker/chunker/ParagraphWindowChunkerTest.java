package com.docint.worker.chunker;

import com.docint.worker.chunker.impl.ParagraphWindowChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParagraphWindowChunkerTest {

    private final TextChunker chunker = new ParagraphWindowChunker();

    @Test
    void chunk_empty() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void chunk_shortText() {
        List<TextChunker.Chunk> chunks = chunker.chunk("Short single sentence.");
        assertEquals(1, chunks.size());
        assertEquals("Short single sentence.", chunks.get(0).getContent());
    }

    @Test
    void chunk_longTextProducesMultiple() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("This is paragraph sentence ").append(i).append(". ");
        }

        List<TextChunker.Chunk> chunks = chunker.chunk(sb.toString(), 256, 0.1);
        assertTrue(chunks.size() > 1);
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
            assertFalse(chunks.get(i).getContent().isBlank());
        }
    }
}
