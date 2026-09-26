package com.docint.query.service.impl;

import com.docint.common.dto.SourceChunk;
import com.docint.common.dto.conversation.*;
import com.docint.common.entity.Conversation;
import com.docint.common.entity.Document;
import com.docint.common.entity.Message;
import com.docint.common.entity.MessageCitation;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.enums.MessageRole;
import com.docint.common.exception.DocumentNotFoundException;
import com.docint.query.llm.ResilientRagLlmClient;
import com.docint.query.repository.ConversationRepository;
import com.docint.query.repository.DocumentQueryRepository;
import com.docint.query.repository.MessageCitationRepository;
import com.docint.query.repository.MessageRepository;
import com.docint.query.search.HybridSearchProvider;
import com.docint.query.service.ConversationService;
import com.docint.query.service.QueryRewritingService;
import com.docint.query.service.RagPromptBuilder;
import com.docint.query.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultConversationService implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentQueryRepository documentRepository;
    private final HybridSearchProvider searchProvider;
    private final ResilientRagLlmClient llmClient;
    private final RagPromptBuilder promptBuilder;
    private final QueryRewritingService queryRewritingService;
    private final RateLimiter rateLimiter;

    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, UUID userId) {
        String docName = null;
        if (request.documentId() != null) {
            Document doc = validateDocument(request.documentId(), userId);
            docName = doc.getFilename();
        }

        String title = request.title();
        if (title == null || title.isBlank()) {
            title = docName != null ? "Chat: " + docName : "New Conversation";
        }

        Conversation conversation = Conversation.builder()
                .userId(userId)
                .documentId(request.documentId())
                .title(title.trim())
                .build();

        conversation = conversationRepository.save(conversation);
        return mapToResponse(conversation, docName, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(UUID userId) {
        List<Conversation> list = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return list.stream().map(c -> {
            String docName = null;
            if (c.getDocumentId() != null) {
                docName = documentRepository.findById(c.getDocumentId())
                        .map(Document::getFilename)
                        .orElse("Unknown Document");
            }
            int msgCount = messageRepository.countByConversationId(c.getId());
            return mapToResponse(c, docName, msgCount);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        Conversation conv = getConversationOrThrow(conversationId, userId);
        String docName = null;
        if (conv.getDocumentId() != null) {
            docName = documentRepository.findById(conv.getDocumentId())
                    .map(Document::getFilename)
                    .orElse(null);
        }
        int msgCount = messageRepository.countByConversationId(conv.getId());
        return mapToResponse(conv, docName, msgCount);
    }

    @Override
    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        Conversation conv = getConversationOrThrow(conversationId, userId);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        if (!messages.isEmpty()) {
            List<UUID> msgIds = messages.stream().map(Message::getId).toList();
            citationRepository.deleteByMessageIdIn(msgIds);
            messageRepository.deleteByConversationId(conv.getId());
        }
        conversationRepository.delete(conv);
        log.info("Deleted conversation {} and all associated messages for user {}", conversationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID conversationId, UUID userId) {
        Conversation conv = getConversationOrThrow(conversationId, userId);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        if (messages.isEmpty()) {
            return List.of();
        }

        List<UUID> msgIds = messages.stream().map(Message::getId).toList();
        List<MessageCitation> allCitations = citationRepository.findByMessageIdIn(msgIds);
        Map<UUID, List<CitationResponse>> citationMap = new HashMap<>();

        for (MessageCitation cit : allCitations) {
            citationMap.computeIfAbsent(cit.getMessageId(), k -> new ArrayList<>())
                    .add(new CitationResponse(
                            cit.getDocumentId(),
                            cit.getFilename(),
                            cit.getChunkId(),
                            cit.getPageNumber(),
                            cit.getSnippet(),
                            cit.getScore()));
        }

        return messages.stream().map(m -> new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getRole(),
                m.getContent(),
                citationMap.getOrDefault(m.getId(), List.of()),
                m.getLatencyMs(),
                m.getProvider(),
                m.getCreatedAt()
        )).toList();
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, SendMessageRequest request, UUID userId) {
        long startTime = System.currentTimeMillis();
        rateLimiter.checkRateLimit(userId.toString());
        Conversation conv = getConversationOrThrow(conversationId, userId);

        // 1. Persist User Message
        Message userMsg = Message.builder()
                .conversationId(conv.getId())
                .role(MessageRole.USER)
                .content(request.content().trim())
                .build();
        messageRepository.save(userMsg);

        // Auto-update conversation title if it's still default
        if ("New Conversation".equals(conv.getTitle())) {
            String newTitle = request.content().trim();
            if (newTitle.length() > 40) {
                newTitle = newTitle.substring(0, 37) + "...";
            }
            conv.setTitle(newTitle);
        }

        // 2. Query Rewriting with Bounded History Window (last 6 messages)
        List<Message> recentHistory = messageRepository.findTop6ByConversationIdOrderByCreatedAtDesc(conv.getId());
        Collections.reverse(recentHistory); // Chronological order
        String rewrittenQuery = queryRewritingService.rewriteQuery(recentHistory, request.content());

        // 3. Retrieval & Synthesis
        RAGResult rag = executeRag(rewrittenQuery, userId, conv.getDocumentId());
        long latency = System.currentTimeMillis() - startTime;

        // 4. Persist Assistant Message
        Message assistantMsg = Message.builder()
                .conversationId(conv.getId())
                .role(MessageRole.ASSISTANT)
                .content(rag.answer())
                .rewrittenQuery(rewrittenQuery)
                .latencyMs(latency)
                .provider(rag.provider())
                .build();
        assistantMsg = messageRepository.save(assistantMsg);

        // 5. Persist Citations
        List<CitationResponse> citationResponses = new ArrayList<>();
        if (rag.sources() != null && !rag.sources().isEmpty()) {
            for (SourceChunk sc : rag.sources()) {
                MessageCitation cit = MessageCitation.builder()
                        .messageId(assistantMsg.getId())
                        .documentId(sc.documentId())
                        .filename(sc.filename())
                        .chunkId(sc.chunkId())
                        .pageNumber(sc.pageNumber())
                        .snippet(sc.content().length() > 300 ? sc.content().substring(0, 297) + "..." : sc.content())
                        .score(sc.score())
                        .build();
                citationRepository.save(cit);

                citationResponses.add(new CitationResponse(
                        cit.getDocumentId(), cit.getFilename(), cit.getChunkId(),
                        cit.getPageNumber(), cit.getSnippet(), cit.getScore()));
            }
        }

        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);

        return new MessageResponse(
                assistantMsg.getId(),
                conv.getId(),
                assistantMsg.getRole(),
                assistantMsg.getContent(),
                citationResponses,
                latency,
                assistantMsg.getProvider(),
                assistantMsg.getCreatedAt()
        );
    }

    @Override
    public void streamMessage(UUID conversationId, SendMessageRequest request, UUID userId, SseEmitter emitter) {
        sseExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                rateLimiter.checkRateLimit(userId.toString());
                Conversation conv = getConversationOrThrow(conversationId, userId);

                // 1. Save user message
                Message userMsg = Message.builder()
                        .conversationId(conv.getId())
                        .role(MessageRole.USER)
                        .content(request.content().trim())
                        .build();
                messageRepository.save(userMsg);

                // Auto-update conversation title if it's still default
                if ("New Conversation".equals(conv.getTitle())) {
                    String newTitle = request.content().trim();
                    if (newTitle.length() > 40) {
                        newTitle = newTitle.substring(0, 37) + "...";
                    }
                    conv.setTitle(newTitle);
                    conversationRepository.save(conv);
                }

                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "RETRIEVING", "message", "Retrieving relevant context...")));

                // 2. Query Rewriting with Bounded History Window
                List<Message> recentHistory = messageRepository.findTop6ByConversationIdOrderByCreatedAtDesc(conv.getId());
                Collections.reverse(recentHistory);
                String rewrittenQuery = queryRewritingService.rewriteQuery(recentHistory, request.content());

                // 3. Retrieval
                float[] queryVector = llmClient.embedQuery(rewrittenQuery);
                List<SourceChunk> chunks = searchProvider.search(
                        rewrittenQuery, queryVector, userId.toString(), conv.getDocumentId());

                List<CitationResponse> citationResponses = new ArrayList<>();
                if (!chunks.isEmpty()) {
                    for (SourceChunk sc : chunks) {
                        citationResponses.add(new CitationResponse(
                                sc.documentId(), sc.filename(), sc.chunkId(),
                                sc.pageNumber(),
                                sc.content().length() > 300 ? sc.content().substring(0, 297) + "..." : sc.content(),
                                sc.score()));
                    }
                }
                emitter.send(SseEmitter.event().name("citations").data(citationResponses));

                // 4. Synthesis
                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "GENERATING", "message", "Synthesizing answer...")));
                String fullAnswer;
                String provider;

                if (chunks.isEmpty()) {
                    fullAnswer = "No relevant information found in your documents for this question.";
                    provider = "none";
                    emitter.send(SseEmitter.event().name("token").data(fullAnswer));
                } else {
                    String prompt = promptBuilder.buildPrompt(rewrittenQuery, chunks);
                    var llmAns = llmClient.generateAnswer(prompt);
                    fullAnswer = llmAns.answer();
                    provider = llmAns.provider();

                    // Stream words/tokens
                    String[] words = fullAnswer.split("(?<=\\s)");
                    for (String word : words) {
                        emitter.send(SseEmitter.event().name("token").data(word));
                        Thread.sleep(15); // Smooth typewriter effect
                    }
                }

                long latency = System.currentTimeMillis() - startTime;

                // 5. Persist Assistant Message
                Message assistantMsg = Message.builder()
                        .conversationId(conv.getId())
                        .role(MessageRole.ASSISTANT)
                        .content(fullAnswer)
                        .rewrittenQuery(rewrittenQuery)
                        .latencyMs(latency)
                        .provider(provider)
                        .build();
                assistantMsg = messageRepository.save(assistantMsg);

                for (CitationResponse cr : citationResponses) {
                    MessageCitation cit = MessageCitation.builder()
                            .messageId(assistantMsg.getId())
                            .documentId(cr.documentId())
                            .filename(cr.filename())
                            .chunkId(cr.chunkId())
                            .pageNumber(cr.pageNumber())
                            .snippet(cr.snippet())
                            .score(cr.score())
                            .build();
                    citationRepository.save(cit);
                }

                conv.setUpdatedAt(Instant.now());
                conversationRepository.save(conv);

                MessageResponse finalResp = new MessageResponse(
                        assistantMsg.getId(),
                        conv.getId(),
                        assistantMsg.getRole(),
                        assistantMsg.getContent(),
                        citationResponses,
                        latency,
                        assistantMsg.getProvider(),
                        assistantMsg.getCreatedAt()
                );

                emitter.send(SseEmitter.event().name("done").data(finalResp));
                emitter.complete();

            } catch (Exception ex) {
                log.error("SSE stream error: {}", ex.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", ex.getMessage())));
                } catch (Exception ignored) {}
                emitter.completeWithError(ex);
            }
        });
    }

    private RAGResult executeRag(String query, UUID userId, UUID documentId) {
        try {
            float[] queryVector = llmClient.embedQuery(query);
            List<SourceChunk> chunks = searchProvider.search(query, queryVector, userId.toString(), documentId);

            if (chunks.isEmpty()) {
                return new RAGResult(
                        "No relevant information found in your documents for this question. " +
                        "Try uploading more documents or asking about a different topic.",
                        List.of(), "none");
            }

            String prompt = promptBuilder.buildPrompt(query, chunks);
            var answer = llmClient.generateAnswer(prompt);
            return new RAGResult(answer.answer(), chunks, answer.provider());
        } catch (Exception e) {
            log.error("RAG execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("RAG execution failed: " + e.getMessage(), e);
        }
    }

    private Conversation getConversationOrThrow(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private Document validateDocument(UUID docId, UUID userId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new DocumentNotFoundException(docId));
        if (!doc.getOwnerId().equals(userId.toString())) {
            throw new DocumentNotFoundException(docId);
        }
        if (doc.getStatus() != DocumentStatus.READY) {
            throw new IllegalArgumentException("Document " + docId + " is not ready yet (" + doc.getStatus() + ")");
        }
        return doc;
    }

    private ConversationResponse mapToResponse(Conversation conv, String docName, int messageCount) {
        return new ConversationResponse(
                conv.getId(),
                conv.getDocumentId(),
                docName,
                conv.getTitle(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                messageCount
        );
    }

    private record RAGResult(String answer, List<SourceChunk> sources, String provider) {}
}
