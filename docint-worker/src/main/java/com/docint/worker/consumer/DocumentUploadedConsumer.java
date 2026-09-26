package com.docint.worker.consumer;

import com.docint.common.event.DocumentEvent;
import com.docint.common.util.CorrelationIdUtil;
import com.docint.worker.service.DocumentProcessingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentUploadedConsumer {

    private final DocumentProcessingOrchestrator orchestrator;

    @KafkaListener(
            topics = "${app.kafka.topics.document-uploaded:document.uploaded}",
            groupId = "docint-worker-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDocumentUploaded(ConsumerRecord<String, DocumentEvent> record) {
        DocumentEvent event = record.value();

        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdUtil.generate();
        }
        CorrelationIdUtil.set(correlationId);

        try {
            log.info("Worker received DOCUMENT_UPLOADED event: docId={}, file={}",
                    event.getPayload().getDocumentId(), event.getPayload().getFilename());

            orchestrator.process(event.getPayload().getDocumentId(), correlationId);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
}
