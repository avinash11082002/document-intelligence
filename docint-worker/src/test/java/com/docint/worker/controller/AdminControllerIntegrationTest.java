package com.docint.worker.controller;

import com.docint.common.dto.DocumentDetailResponse;
import com.docint.common.dto.ReplayResponse;
import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import com.docint.common.event.DocumentEvent;
import com.docint.worker.repository.DocumentWorkerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentWorkerRepository documentRepository;

    @MockBean
    private KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    @Test
    @DisplayName("End-to-End: POST /api/admin/documents/{id}/replay resets FAILED document and re-queues onto Kafka")
    void replayDocument_success() throws Exception {
        UUID docId = UUID.randomUUID();
        Document failedDoc = Document.builder()
                .id(docId)
                .ownerId("user-101")
                .filename("failed_invoice.pdf")
                .contentType("application/pdf")
                .fileSizeBytes(2048L)
                .contentHash("hash123")
                .filePath("/data/uploads/failed_invoice.pdf")
                .status(DocumentStatus.FAILED)
                .retryCount(3)
                .failureReason("LLM timeout")
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(failedDoc));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/admin/documents/" + docId + "/replay"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        verify(kafkaTemplate, times(1)).send(any(), eq(docId.toString()), any(DocumentEvent.class));
    }
}
