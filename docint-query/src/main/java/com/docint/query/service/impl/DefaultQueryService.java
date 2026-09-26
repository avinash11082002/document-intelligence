package com.docint.query.service.impl;

import com.docint.common.dto.QueryRequest;
import com.docint.common.dto.QueryResponse;
import com.docint.common.dto.SourceChunk;
import com.docint.common.entity.Document;
import com.docint.common.entity.QueryLog;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.exception.DocumentNotFoundException;
import com.docint.common.util.HashUtil;
import com.docint.query.llm.ResilientRagLlmClient;
import com.docint.query.repository.DocumentQueryRepository;
import com.docint.query.repository.QueryLogRepository;
import com.docint.query.search.HybridSearchProvider;
import com.docint.query.service.QueryCache;
import com.docint.query.service.QueryService;
import com.docint.query.service.RagPromptBuilder;
import com.docint.query.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultQueryService implements QueryService {

    private final RateLimiter rateLimiter;
    private final QueryCache queryCache;
    private final HybridSearchProvider searchProvider;
    private final ResilientRagLlmClient llmClient;
    private final RagPromptBuilder promptBuilder;
    private final QueryLogRepository queryLogRepository;
    private final DocumentQueryRepository documentRepository;

    @Override
    public QueryResponse query(QueryRequest request, UUID userId) {
        long startTime = System.currentTimeMillis();
        // userId comes from JWT via SecurityContext — never from request body
        String ownerId = userId.toString();

        // 1. Sliding Window Rate Limiter
        rateLimiter.checkRateLimit(ownerId);

        // 2. Validate document ownership + state (if scoped to specific document)
        if (request.documentId() != null) {
            Document doc = documentRepository.findById(request.documentId())
                    .orElseThrow(() -> new DocumentNotFoundException(request.documentId()));
            // Authorization check: ensure user owns the document
            if (!doc.getOwnerId().equals(ownerId)) {
                throw new DocumentNotFoundException(request.documentId()); // deliberately opaque
            }
            if (doc.getStatus() != DocumentStatus.READY) {
                throw new IllegalArgumentException(
                        "Document " + request.documentId() + " is not yet ready (status: " + doc.getStatus() + "). " +
                        "Please wait for processing to complete.");
            }
        }

        // 3. Check Redis Cache
        var cached = queryCache.get(ownerId, request.documentId(), request.question());
        if (cached.isPresent()) {
            long latency = System.currentTimeMillis() - startTime;
            QueryResponse res = cached.get();
            QueryResponse finalRes = new QueryResponse(res.answer(), res.sourceChunks(), true, res.llmProvider(), latency);
            logAudit(request, ownerId, finalRes, latency);
            return finalRes;
        }

        try {
            // 4. Query Embedding
            float[] queryVector = llmClient.embedQuery(request.question());

            // 5. Hybrid Vector + BM25 Search (scoped to authenticated user's documents)
            List<SourceChunk> chunks = searchProvider.search(
                    request.question(), queryVector, ownerId, request.documentId());

            if (chunks.isEmpty()) {
                long latency = System.currentTimeMillis() - startTime;
                QueryResponse emptyRes = new QueryResponse(
                        "No relevant information found in your documents for this question. " +
                        "Try uploading more documents or rephrasing your question.",
                        List.of(), false, "none", latency);
                logAudit(request, ownerId, emptyRes, latency);
                return emptyRes;
            }

            // 6. Grounded Prompt Synthesis & Generation
            String prompt = promptBuilder.buildPrompt(request.question(), chunks);
            var llmAnswer = llmClient.generateAnswer(prompt);

            long latency = System.currentTimeMillis() - startTime;
            QueryResponse response = new QueryResponse(
                    llmAnswer.answer(), chunks, false, llmAnswer.provider(), latency);

            // 7. Populate Redis Cache
            queryCache.put(ownerId, request.documentId(), request.question(), response);

            // 8. Log Audit Record
            logAudit(request, ownerId, response, latency);

            log.info("RAG query completed in {}ms (provider: {}, matches: {})",
                    latency, llmAnswer.provider(), chunks.size());
            return response;

        } catch (RuntimeException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Query failed after {}ms: {}", latency, e.getMessage());
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Query failed after {}ms: {}", latency, e.getMessage());
            throw new RuntimeException("Query execution failed: " + e.getMessage(), e);
        }
    }

    private void logAudit(QueryRequest request, String ownerId, QueryResponse response, long latency) {
        try {
            QueryLog audit = QueryLog.builder()
                    .ownerId(ownerId)
                    .documentId(request.documentId())
                    .question(request.question())
                    .questionHash(HashUtil.hashQuestion(request.question()))
                    .answer(response.answer())
                    .sourceChunks(response.sourceChunks().stream()
                            .map(c -> Map.<String, Object>of(
                                    "chunkId", c.chunkId(),
                                    "filename", c.filename(),
                                    "score", c.score()))
                            .toList())
                    .cacheHit(response.cached())
                    .latencyMs((int) latency)
                    .llmProvider(response.llmProvider())
                    .build();
            queryLogRepository.save(audit);
        } catch (Exception ex) {
            log.warn("Failed to persist query audit log: {}", ex.getMessage());
        }
    }
}
