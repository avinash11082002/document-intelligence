package com.docint.worker.service;

import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.enums.EventType;
import com.docint.common.event.DocumentEvent;
import com.docint.common.util.CorrelationIdUtil;
import com.docint.worker.chunker.TextChunker;
import com.docint.worker.extractor.TextExtractor;
import com.docint.worker.llm.EmbeddingLlmClient;
import com.docint.worker.repository.DocumentWorkerRepository;
import com.docint.worker.search.ChunkIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingOrchestrator {

    private final DocumentWorkerRepository documentRepository;
    private final TextExtractor textExtractor;
    private final TextChunker textChunker;
    private final LlmClassificationService classificationService;
    private final EmbeddingLlmClient embeddingClient;
    private final ChunkIndexer chunkIndexer;
    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    @Value("${app.kafka.topics.document-indexed:document.indexed}")
    private String documentIndexedTopic;

    @Value("${app.kafka.topics.document-processing-dlq:document.processing.dlq}")
    private String dlqTopic;

    @Value("${app.upload-dir:/data/uploads}")
    private String uploadDir;

    public void process(UUID documentId, String correlationId) {
        CorrelationIdUtil.set(correlationId);
        log.info("Starting processing pipeline for docId={}", documentId);

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("Document {} not found in database", documentId);
            return;
        }

        // Idempotency guard — skip already-completed documents
        if (document.getStatus() == DocumentStatus.READY) {
            log.info("Document {} already READY. Skipping.", documentId);
            return;
        }

        updateStatus(document, DocumentStatus.PROCESSING, null);

        try {
            // ── Stage 1: Text Extraction ──────────────────────────────────────
            log.debug("[1/5] Extracting text for {}", document.getFilename());
            updateStatus(document, DocumentStatus.EXTRACTING, null);
            String resolvedPath = resolvePath(document.getFilePath());
            TextExtractor.ExtractionResult extraction = textExtractor.extractText(resolvedPath);

            if (extraction.text() == null || extraction.text().isBlank()) {
                throw new IllegalStateException("Empty content extracted from: " + document.getFilename());
            }
            int pageCount = extraction.pageCount() > 0 ? extraction.pageCount() : 1;

            // ── Stage 2: Chunking ─────────────────────────────────────────────
            log.debug("[2/5] Chunking content ({} chars)", extraction.text().length());
            updateStatus(document, DocumentStatus.CHUNKING, null);
            List<TextChunker.Chunk> chunks = textChunker.chunk(extraction.text());

            // ── Stage 3: LLM Classification & Enrichment ──────────────────────
            log.debug("[3/5] Classifying document via LLM");
            var classification = classificationService.classify(extraction.text(), document.getFilename());
            document.setDocumentType(classification.documentType());
            document.setExtractedFields(classification.extractedFields());
            document.setSummary(classification.summary());
            document.setSuggestedQuestions(classification.suggestedQuestions());
            document.setPageCount(pageCount);

            // ── Stage 4: Vector Embeddings ────────────────────────────────────
            log.debug("[4/5] Generating embeddings for {} chunks", chunks.size());
            updateStatus(document, DocumentStatus.EMBEDDING, null);
            List<String> chunkContents = chunks.stream().map(TextChunker.Chunk::getContent).toList();
            List<float[]> embeddings = embeddingClient.embedAll(chunkContents);

            // ── Stage 5: Elasticsearch Indexing ──────────────────────────────
            log.debug("[5/5] Indexing into Elasticsearch");
            updateStatus(document, DocumentStatus.INDEXING, null);
            chunkIndexer.indexChunks(document, chunks, embeddings);

            document.setChunkCount(chunks.size());
            document.setProcessedAt(Instant.now());
            updateStatus(document, DocumentStatus.READY, null);

            publishReadyEvent(document);
            log.info("Document {} READY (chunks={}, pages={})", documentId, chunks.size(), pageCount);

        } catch (Exception e) {
            log.error("Processing failed for docId={}: {}", documentId, e.getMessage(), e);
            handleFailure(document, e);
        }
    }

    private void handleFailure(Document document, Exception ex) {
        document.setRetryCount(document.getRetryCount() + 1);
        String failureReason = ex.getMessage() != null
                ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 2000))
                : "Unknown error";
        updateStatus(document, DocumentStatus.FAILED, failureReason);

        DocumentEvent dlqEvent = DocumentEvent.builder()
                .eventType(EventType.PROCESSING_FAILED)
                .correlationId(CorrelationIdUtil.getOrGenerate())
                .payload(DocumentEvent.Payload.builder()
                        .documentId(document.getId())
                        .ownerId(document.getOwnerId())
                        .originalTopic("document.uploaded")
                        .retryCount(document.getRetryCount())
                        .lastError(failureReason)
                        .lastErrorTimestamp(Instant.now())
                        .build())
                .build();

        kafkaTemplate.send(dlqTopic, document.getId().toString(), dlqEvent);
        log.warn("Published to DLQ '{}' for docId={}, retryCount={}",
                dlqTopic, document.getId(), document.getRetryCount());
    }

    private void publishReadyEvent(Document document) {
        DocumentEvent event = DocumentEvent.builder()
                .eventType(EventType.DOCUMENT_INDEXED)
                .correlationId(CorrelationIdUtil.getOrGenerate())
                .payload(DocumentEvent.Payload.builder()
                        .documentId(document.getId())
                        .ownerId(document.getOwnerId())
                        .filename(document.getFilename())
                        .contentType(document.getContentType())
                        .fileSizeBytes(document.getFileSizeBytes())
                        .contentHash(document.getContentHash())
                        .filePath(document.getFilePath())
                        .build())
                .build();

        kafkaTemplate.send(documentIndexedTopic, document.getId().toString(), event);
    }

    @Transactional
    protected void updateStatus(Document document, DocumentStatus status, String failureReason) {
        document.setStatus(status);
        if (failureReason != null) {
            document.setFailureReason(failureReason);
        }
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
    }

    private String resolvePath(String relativeOrAbsolute) {
        java.nio.file.Path path = java.nio.file.Path.of(relativeOrAbsolute);
        if (path.isAbsolute()) {
            return relativeOrAbsolute;
        }
        String[] parts = relativeOrAbsolute.split("/", 2);
        if (parts.length > 1) {
            return java.nio.file.Path.of(uploadDir, parts[1]).toString();
        }
        return java.nio.file.Path.of(uploadDir, relativeOrAbsolute).toString();
    }
}
