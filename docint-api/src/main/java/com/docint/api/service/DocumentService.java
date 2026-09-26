package com.docint.api.service;

import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.DocumentUploadResponse;
import com.docint.common.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(MultipartFile file, UUID userId) throws IOException;

    DocumentDetailResponse getDocument(UUID documentId, UUID userId);

    Page<DocumentDetailResponse> listDocuments(UUID userId, DocumentStatus status, Pageable pageable);

    List<DocumentDetailResponse> searchDocuments(UUID userId, String query);

    void deleteDocument(UUID documentId, UUID userId);

    DocumentDetailResponse retryDocument(UUID documentId, UUID userId);
}
