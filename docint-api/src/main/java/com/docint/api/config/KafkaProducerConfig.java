package com.docint.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Value("${app.kafka.topics.document-uploaded:document.uploaded}")
    private String documentUploadedTopic;

    @Bean
    public NewTopic documentUploadedTopic() {
        return TopicBuilder.name(documentUploadedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
