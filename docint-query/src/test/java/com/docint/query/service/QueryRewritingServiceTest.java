package com.docint.query.service;

import com.docint.common.entity.Message;
import com.docint.common.enums.MessageRole;
import com.docint.query.llm.ResilientRagLlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryRewritingServiceTest {

    @Mock
    private ResilientRagLlmClient llmClient;

    @InjectMocks
    private QueryRewritingService queryRewritingService;

    @Test
    @DisplayName("Empty history returns question as-is without invoking LLM")
    void rewriteQuery_emptyHistory_returnsOriginal() {
        String query = "What is the termination clause?";
        String result = queryRewritingService.rewriteQuery(List.of(), query);

        assertEquals(query, result);
        verify(llmClient, never()).generateAnswer(anyString());
    }

    @Test
    @DisplayName("With conversation history, invokes LLM to formulate standalone query")
    void rewriteQuery_withHistory_invokesLlm() {
        UUID convId = UUID.randomUUID();
        List<Message> history = List.of(
                Message.builder()
                        .id(UUID.randomUUID())
                        .conversationId(convId)
                        .role(MessageRole.USER)
                        .content("Tell me about the contract with Acme Corp")
                        .createdAt(Instant.now().minusSeconds(60))
                        .build(),
                Message.builder()
                        .id(UUID.randomUUID())
                        .conversationId(convId)
                        .role(MessageRole.ASSISTANT)
                        .content("The contract with Acme Corp was signed on Jan 15, 2024 for 2 years.")
                        .createdAt(Instant.now().minusSeconds(30))
                        .build()
        );

        when(llmClient.generateAnswer(anyString()))
                .thenReturn(new ResilientRagLlmClient.RagLlmAnswer(
                        "What is the renewal date of the Acme Corp contract?", "gemini"));

        String rewritten = queryRewritingService.rewriteQuery(history, "When does it renew?");

        assertEquals("What is the renewal date of the Acme Corp contract?", rewritten);
        verify(llmClient, times(1)).generateAnswer(anyString());
    }

    @Test
    @DisplayName("LLM exception falls back gracefully to original query")
    void rewriteQuery_llmException_fallsBackToOriginal() {
        UUID convId = UUID.randomUUID();
        List<Message> history = List.of(
                Message.builder()
                        .id(UUID.randomUUID())
                        .conversationId(convId)
                        .role(MessageRole.USER)
                        .content("What is the invoice amount?")
                        .build()
        );

        when(llmClient.generateAnswer(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        String result = queryRewritingService.rewriteQuery(history, "What about tax?");
        assertEquals("What about tax?", result);
    }
}
