package com.docint.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "query_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "question_hash", nullable = false)
    private String questionHash;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_chunks", columnDefinition = "jsonb")
    private List<Map<String, Object>> sourceChunks;

    @Column(name = "cache_hit", nullable = false)
    @Builder.Default
    private Boolean cacheHit = false;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "llm_provider")
    private String llmProvider;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
