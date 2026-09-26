package com.docint.query.service;

import com.docint.common.dto.SourceChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagPromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an expert document analysis assistant. Answer the user's question based strictly on the provided context passages.
            
            Guidelines:
            1. Rely solely on the provided context. If the answer cannot be determined from the context, explicitly state: "I cannot find this information in the uploaded documents."
            2. When possible, cite the document name or section where the fact appears.
            3. Keep the response accurate, concise, and structured.
            """;

    public String buildPrompt(String question, List<SourceChunk> contextChunks) {
        String formattedContext = contextChunks.stream()
                .map(chunk -> String.format(
                        "[Document: %s | Match Score: %.2f]\n%s",
                        chunk.filename(), chunk.score(), chunk.content()))
                .collect(Collectors.joining("\n\n---\n\n"));

        return SYSTEM_INSTRUCTIONS + "\n\n" +
                "=== CONTEXT PASSAGES ===\n" +
                formattedContext + "\n\n" +
                "=== USER QUESTION ===\n" +
                question + "\n\n" +
                "=== ANSWER ===\n";
    }
}
