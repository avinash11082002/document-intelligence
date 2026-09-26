package com.docint.api.controller;

import com.docint.api.exception.GlobalExceptionHandler;
import com.docint.api.security.AuthenticatedUser;
import com.docint.api.security.JwtAuthenticationFilter;
import com.docint.api.service.DocumentService;
import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.DocumentUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AuthenticatedUser principal = new AuthenticatedUser(testUserId, "testuser", "test-token");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("End-to-End: POST /api/documents uploads document and returns 201 Created")
    void uploadDocument_endToEnd_returnsCreated() throws Exception {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "Vendor: Apex Cloud, Total: $1,200.00".getBytes());

        DocumentUploadResponse uploadResponse = DocumentUploadResponse.uploaded(
                docId, "invoice.pdf", "application/pdf", (long) file.getBytes().length,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "UPLOADED", Instant.now());

        when(documentService.uploadDocument(any(), eq(testUserId))).thenReturn(uploadResponse);

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("X-Correlation-Id", "corr-test-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("End-to-End: GET /api/documents/{id} returns ready document with extracted fields")
    void getDocument_endToEnd_returnsIndexedDoc() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDetailResponse detailResponse = new DocumentDetailResponse(
                docId, "invoice.pdf", "application/pdf", 1024L,
                "hash123", "READY", "invoice", "Invoice summary", 1,
                List.of("What is the invoice amount?"),
                Map.of("vendor", "Apex Cloud", "total_amount", "$1,200.00"),
                3, null, 0, Instant.now(), Instant.now(), Instant.now());

        when(documentService.getDocument(eq(docId), eq(testUserId))).thenReturn(detailResponse);

        mockMvc.perform(get("/api/documents/" + docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.documentType").value("invoice"))
                .andExpect(jsonPath("$.extractedFields.vendor").value("Apex Cloud"))
                .andExpect(jsonPath("$.status").value("READY"));
    }
}
