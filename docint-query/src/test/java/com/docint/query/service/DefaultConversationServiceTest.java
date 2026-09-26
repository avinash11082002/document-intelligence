package com.docint.query.service;

import com.docint.common.dto.SourceChunk;
import com.docint.common.dto.conversation.ConversationResponse;
import com.docint.common.dto.conversation.CreateConversationRequest;
import com.docint.common.dto.conversation.MessageResponse;
import com.docint.common.dto.conversation.SendMessageRequest;
import com.docint.common.entity.Conversation;
import com.docint.common.entity.Document;
import com.docint.common.entity.Message;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.enums.MessageRole;
import com.docint.query.llm.ResilientRagLlmClient;
import com.docint.query.repository.ConversationRepository;
import com.docint.query.repository.DocumentQueryRepository;
import com.docint.query.repository.MessageCitationRepository;
import com.docint.query.repository.MessageRepository;
import com.docint.query.search.HybridSearchProvider;
import com.docint.query.service.impl.DefaultConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageCitationRepository citationRepository;
    @Mock private DocumentQueryRepository documentRepository;
    @Mock private HybridSearchProvider searchProvider;
    @Mock private ResilientRagLlmClient llmClient;
    @Mock private RagPromptBuilder promptBuilder;
    @Mock private QueryRewritingService queryRewritingService;
    @Mock private RateLimiter rateLimiter;

    @InjectMocks private DefaultConversationService conversationService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Create conversation without document scoping")
    void createConversation_workspaceWide_success() {
        CreateConversationRequest req = new CreateConversationRequest(null, "Workspace Chat");
        UUID convId = UUID.randomUUID();

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(convId);
            return c;
        });

        ConversationResponse resp = conversationService.createConversation(req, userId);

        assertNotNull(resp);
        assertEquals(convId, resp.id());
        assertEquals("Workspace Chat", resp.title());
        assertNull(resp.documentId());
    }

    @Test
    @DisplayName("Create conversation with document scoping validates ownership and READY status")
    void createConversation_documentScoped_success() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
                .id(docId)
                .ownerId(userId.toString())
                .filename("contract.pdf")
                .status(DocumentStatus.READY)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CreateConversationRequest req = new CreateConversationRequest(docId, null);
        ConversationResponse resp = conversationService.createConversation(req, userId);

        assertNotNull(resp);
        assertEquals(docId, resp.documentId());
        assertEquals("Chat: contract.pdf", resp.title());
    }

    @Test
    @DisplayName("Send message executes rewriting, search, and persists response with citations")
    void sendMessage_fullPipeline_success() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Conversation conv = Conversation.builder()
                .id(convId)
                .userId(userId)
                .documentId(docId)
                .title("New Conversation")
                .build();

        when(conversationRepository.findByIdAndUserId(convId, userId)).thenReturn(Optional.of(conv));
        when(messageRepository.findTop6ByConversationIdOrderByCreatedAtDesc(convId)).thenReturn(List.of());
        when(queryRewritingService.rewriteQuery(any(), eq("What is the payment term?")))
                .thenReturn("What is the payment term?");
        when(llmClient.embedQuery(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        List<SourceChunk> chunks = List.of(
                new SourceChunk(docId + "_0", docId, "invoice.pdf", "Net 30 days payment term applies.", 0.92, 1)
        );
        when(searchProvider.search(eq("What is the payment term?"), any(), eq(userId.toString()), eq(docId)))
                .thenReturn(chunks);
        when(promptBuilder.buildPrompt(anyString(), eq(chunks))).thenReturn("Prompt text");
        when(llmClient.generateAnswer("Prompt text"))
                .thenReturn(new ResilientRagLlmClient.RagLlmAnswer("The payment term is Net 30 days.", "gemini"));

        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        SendMessageRequest req = new SendMessageRequest("What is the payment term?");
        MessageResponse resp = conversationService.sendMessage(convId, req, userId);

        assertNotNull(resp);
        assertEquals(MessageRole.ASSISTANT, resp.role());
        assertEquals("The payment term is Net 30 days.", resp.content());
        assertEquals(1, resp.citations().size());
        assertEquals("invoice.pdf", resp.citations().getFirst().filename());
        verify(citationRepository, times(1)).save(any());
    }
}
