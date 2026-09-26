package com.docint.api.service.impl;

import com.docint.api.repository.DocumentRepository;
import com.docint.api.service.DocumentEventPublisher;
import com.docint.api.service.DocumentService;
import com.docint.api.service.DocumentStorageService;
import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.DocumentUploadResponse;
import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.exception.AccessDeniedException;
import com.docint.common.exception.DocumentNotFoundException;
import com.docint.common.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDocumentService implements DocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "text/plain",
            "image/png",
            "image/jpeg",
            "image/tiff"
    );

    @Value("${app.upload.max-file-size-bytes:104857600}") // 100MB
    private long maxFileSizeBytes = 104_857_600L;

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentEventPublisher eventPublisher;

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, UUID userId) throws IOException {
        validateFile(file);

        String ownerId = userId.toString();
        String contentHash = HashUtil.sha256(file.getBytes());

        // Graceful duplicate detection — return existing document info, not a raw 409
        var existing = documentRepository.findByContentHashAndOwnerId(contentHash, ownerId);
        if (existing.isPresent()) {
            Document dup = existing.get();
            log.info("Duplicate document detected: existing id={}, hash={}", dup.getId(), contentHash);
            return DocumentUploadResponse.duplicate(dup.getId(), dup.getFilename(), contentHash, dup.getCreatedAt());
        }

        Document document = Document.builder()
                .ownerId(ownerId)
                .filename(sanitizeFilename(file.getOriginalFilename()))
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .contentHash(contentHash)
                .filePath("")
                .status(DocumentStatus.UPLOADED)
                .build();
        document = documentRepository.save(document);

        String filePath = documentStorageService.store(document.getId(), file);
        document.setFilePath(filePath);
        document = documentRepository.save(document);

        eventPublisher.publishUploadedEvent(document);

        log.info("Document uploaded: id={}, file={}, size={}", document.getId(),
                document.getFilename(), document.getFileSizeBytes());

        return DocumentUploadResponse.uploaded(
                document.getId(), document.getFilename(), document.getContentType(),
                document.getFileSizeBytes(), document.getContentHash(),
                document.getStatus().name(), document.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocument(UUID documentId, UUID userId) {
        Document document = findAndAuthorize(documentId, userId);
        return toDetailResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentDetailResponse> listDocuments(UUID userId, DocumentStatus status, Pageable pageable) {
        String ownerId = userId.toString();
        Page<Document> page = (status != null)
                ? documentRepository.findByOwnerIdAndStatus(ownerId, status, pageable)
                : documentRepository.findByOwnerId(ownerId, pageable);
        return page.map(this::toDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDetailResponse> searchDocuments(UUID userId, String query) {
        return documentRepository.searchByOwnerIdAndQuery(userId.toString(), query)
                .stream().map(this::toDetailResponse).toList();
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = findAndAuthorize(documentId, userId);

        try {
            documentStorageService.delete(documentId);
        } catch (IOException e) {
            log.warn("Could not delete physical file for document {}: {}", documentId, e.getMessage());
        }

        documentRepository.delete(document);
        log.info("Document deleted: id={}, file={}", documentId, document.getFilename());
    }

    @Override
    @Transactional
    public DocumentDetailResponse retryDocument(UUID documentId, UUID userId) {
        Document document = findAndAuthorize(documentId, userId);

        if (document.getStatus() != DocumentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only FAILED documents can be retried. Current status: " + document.getStatus());
        }

        document.setStatus(DocumentStatus.UPLOADED);
        document.setFailureReason(null);
        document.setUpdatedAt(Instant.now());
        document = documentRepository.save(document);

        eventPublisher.publishUploadedEvent(document);
        log.info("Document retry initiated: id={}", documentId);

        return toDetailResponse(document);
    }

    // ---- helpers ----

    private Document findAndAuthorize(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (!document.getOwnerId().equals(userId.toString())) {
            throw new AccessDeniedException("Access denied to document: " + documentId);
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file cannot be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size exceeds the maximum allowed size of 100MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type: " + contentType +
                    ". Allowed: PDF, DOCX, TXT, PNG, JPEG, TIFF");
        }
    }

    /** Prevent path traversal attacks from malicious filenames. */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "untitled";
        return original
                .replaceAll("[\\\\/]", "_")  // no path separators
                .replaceAll("[^a-zA-Z0-9._\\-]", "_")
                .replaceAll("_+", "_")
                .trim();
    }

    private DocumentDetailResponse toDetailResponse(Document doc) {
        return new DocumentDetailResponse(
                doc.getId(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getContentHash(),
                doc.getStatus().name(),
                doc.getDocumentType(),
                doc.getSummary(),
                doc.getPageCount(),
                doc.getSuggestedQuestions(),
                doc.getExtractedFields(),
                doc.getChunkCount(),
                doc.getFailureReason(),
                doc.getRetryCount(),
                doc.getCreatedAt(),
                doc.getProcessedAt(),
                doc.getUpdatedAt()
        );
    }
}
