package com.docint.query.consumer;

import com.docint.common.event.DocumentEvent;
import com.docint.query.service.QueryCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationConsumer {

    private final QueryCache queryCache;

    @KafkaListener(
            topics = "${app.kafka.topics.document-indexed:document.indexed}",
            groupId = "docint-query-cache-group"
    )
    public void onDocumentIndexed(ConsumerRecord<String, DocumentEvent> record) {
        DocumentEvent event = record.value();
        var payload = event.getPayload();

        log.info("Document indexed event received for docId={} (owner={}). Invalidating caches...",
                payload.getDocumentId(), payload.getOwnerId());

        queryCache.evict(payload.getOwnerId(), payload.getDocumentId());
    }
}
