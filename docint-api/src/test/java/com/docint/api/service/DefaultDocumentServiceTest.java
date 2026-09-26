package com.docint.api.service;

import com.docint.api.repository.DocumentRepository;
import com.docint.api.service.impl.DefaultDocumentService;
import com.docint.common.dto.DocumentUploadResponse;
import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultDocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentStorageService storageService;
    @Mock private DocumentEventPublisher eventPublisher;

    @InjectMocks private DefaultDocumentService documentService;

    private final UUID testUserId = UUID.randomUUID();

    @Test
    void uploadDocument_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "Agreement content here".getBytes());

        when(documentRepository.findByContentHashAndOwnerId(anyString(), eq(testUserId.toString())))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(inv -> {
                    Document d = inv.getArgument(0);
                    if (d.getId() == null) d.setId(UUID.randomUUID());
                    return d;
                });
        when(storageService.store(any(UUID.class), eq(file))).thenReturn("/data/uploads/uuid/contract.pdf");

        DocumentUploadResponse resp = documentService.uploadDocument(file, testUserId);

        assertNotNull(resp.id());
        assertEquals("contract.pdf", resp.filename());
        assertEquals("UPLOADED", resp.status());

        verify(eventPublisher, times(1)).publishUploadedEvent(any(Document.class));
    }

    @Test
    void uploadDocument_duplicateHash_returnsExistingDoc() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "Agreement content here".getBytes());

        UUID existingId = UUID.randomUUID();
        Document existing = Document.builder()
                .id(existingId)
                .ownerId(testUserId.toString())
                .filename("contract.pdf")
                .status(DocumentStatus.READY)
                .build();

        when(documentRepository.findByContentHashAndOwnerId(anyString(), eq(testUserId.toString())))
                .thenReturn(Optional.of(existing));

        DocumentUploadResponse resp = documentService.uploadDocument(file, testUserId);
        assertEquals(existingId, resp.id());
        assertEquals(existingId, resp.existingDocumentId());
        verify(eventPublisher, never()).publishUploadedEvent(any());
    }

    @Test
    void uploadDocument_emptyFile_throwsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> documentService.uploadDocument(empty, testUserId));
    }
}

