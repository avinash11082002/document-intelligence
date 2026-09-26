package com.docint.query.service;

import com.docint.common.dto.SourceChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void buildPrompt_containsQuestionAndContext() {
        List<SourceChunk> chunks = List.of(
                new SourceChunk("c1", UUID.randomUUID(), "terms.pdf", "The fee is $500/month.", 0.92, 1)
        );

        String prompt = builder.buildPrompt("What is the fee?", chunks);

        assertTrue(prompt.contains("What is the fee?"));
        assertTrue(prompt.contains("The fee is $500/month."));
        assertTrue(prompt.contains("terms.pdf"));
        assertTrue(prompt.contains("CONTEXT PASSAGES"));
    }
}
