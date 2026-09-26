package com.docint.worker.controller;

import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.ReplayResponse;
import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.enums.EventType;
import com.docint.common.event.DocumentEvent;
import com.docint.common.exception.DocumentNotFoundException;
import com.docint.common.util.CorrelationIdUtil;
import com.docint.worker.repository.DocumentWorkerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin / DLQ", description = "Administration and Dead-Letter Queue management API")
public class AdminController {

    private final DocumentWorkerRepository documentRepository;
    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    @Value("${app.kafka.topics.document-uploaded:document.uploaded}")
    private String documentUploadedTopic;

    @PostMapping("/documents/{id}/replay")
    @Operation(summary = "Replay failed document from DLQ", description = "Resets FAILED document state to UPLOADED and republishes Kafka event.")
    public ResponseEntity<ReplayResponse> replayDocument(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Owner-Id", required = false) String ownerId) {

        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (document.getStatus() != DocumentStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ReplayResponse(id, document.getStatus().name(),
                            "Cannot replay document in status: " + document.getStatus()));
        }

        document.setStatus(DocumentStatus.UPLOADED);
        document.setRetryCount(0);
        document.setFailureReason(null);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        DocumentEvent event = DocumentEvent.builder()
                .eventType(EventType.DOCUMENT_UPLOADED)
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

        kafkaTemplate.send(documentUploadedTopic, document.getId().toString(), event);
        log.info("Admin replayed document {} back onto topic {}", id, documentUploadedTopic);

        return ResponseEntity.accepted()
                .body(new ReplayResponse(id, "UPLOADED", "Document successfully re-queued for processing"));
    }

    @GetMapping("/dlq/documents")
    @Operation(summary = "List documents currently in DLQ", description = "Returns all documents in FAILED status.")
    public ResponseEntity<Page<DocumentDetailResponse>> listFailedDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Document> failed = documentRepository.findByStatus(
                DocumentStatus.FAILED,
                PageRequest.of(page, Math.min(size, 100), Sort.by("updatedAt").descending()));

        Page<DocumentDetailResponse> response = failed.map(doc ->
                new DocumentDetailResponse(
                        doc.getId(), doc.getFilename(),
                        doc.getContentType(), doc.getFileSizeBytes(), doc.getContentHash(),
                        doc.getStatus().name(), doc.getDocumentType(), doc.getSummary(),
                        doc.getPageCount(), doc.getSuggestedQuestions(), doc.getExtractedFields(),
                        doc.getChunkCount(), doc.getFailureReason(), doc.getRetryCount(),
                        doc.getCreatedAt(), doc.getProcessedAt(), doc.getUpdatedAt()
                ));

        return ResponseEntity.ok(response);
    }
}
