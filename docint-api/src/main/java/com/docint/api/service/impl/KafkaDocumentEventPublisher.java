package com.docint.api.service.impl;

import com.docint.api.service.DocumentEventPublisher;
import com.docint.common.entity.Document;
import com.docint.common.enums.EventType;
import com.docint.common.event.DocumentEvent;
import com.docint.common.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDocumentEventPublisher implements DocumentEventPublisher {

    private final KafkaTemplate<String, DocumentEvent> kafkaTemplate;

    @Value("${app.kafka.topics.document-uploaded:document.uploaded}")
    private String documentUploadedTopic;

    @Override
    public void publishUploadedEvent(Document document) {
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
        log.info("Published DOCUMENT_UPLOADED event to topic '{}' for docId={}",
                documentUploadedTopic, document.getId());
    }
}
