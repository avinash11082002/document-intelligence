package com.docint.worker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Value("${app.kafka.topics.document-uploaded:document.uploaded}")
    private String documentUploadedTopic;

    @Value("${app.kafka.topics.document-indexed:document.indexed}")
    private String documentIndexedTopic;

    @Value("${app.kafka.topics.document-processing-dlq:document.processing.dlq}")
    private String documentProcessingDlqTopic;

    @Bean
    public NewTopic documentUploadedTopic() {
        return TopicBuilder.name(documentUploadedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentIndexedTopic() {
        return TopicBuilder.name(documentIndexedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentProcessingDlqTopic() {
        return TopicBuilder.name(documentProcessingDlqTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // 3 retries with 5s backoff
        return new DefaultErrorHandler(new FixedBackOff(5000L, 3L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // Single concurrency to prevent multiple consumer threads from colliding on external LLM rate limits
        factory.setConcurrency(1);
        return factory;
    }
}
