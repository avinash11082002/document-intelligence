package com.docint.api.controller;

import com.docint.api.security.SecurityUtils;
import com.docint.api.service.DocumentService;
import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.DocumentUploadResponse;
import com.docint.common.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Documents", description = "Document upload, management, and search")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document",
            description = "Upload PDF, DOCX or TXT. Deduplicates via SHA-256. Returns existing document info on duplicate.")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID userId = SecurityUtils.currentUserId();
        DocumentUploadResponse response = documentService.uploadDocument(file, userId);
        // 200 for duplicate (existing doc returned), 201 for new upload
        HttpStatus status = response.existingDocumentId() != null ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document detail and status")
    public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        DocumentDetailResponse response = documentService.getDocument(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List documents", description = "Paginated list of the authenticated user's documents.")
    public ResponseEntity<Page<DocumentDetailResponse>> listDocuments(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        UUID userId = SecurityUtils.currentUserId();
        int clampedSize = Math.min(size, 100);
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Page<DocumentDetailResponse> result = documentService.listDocuments(
                userId, status, PageRequest.of(page, clampedSize, sort));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @Operation(summary = "Search documents by filename or content type")
    public ResponseEntity<List<DocumentDetailResponse>> searchDocuments(
            @RequestParam String q) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(documentService.searchDocuments(userId, q));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document",
            description = "Permanently deletes a document, its file, and its indexed chunks.")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        documentService.deleteDocument(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry failed document processing")
    public ResponseEntity<DocumentDetailResponse> retryDocument(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        DocumentDetailResponse response = documentService.retryDocument(id, userId);
        return ResponseEntity.accepted().body(response);
    }
}
