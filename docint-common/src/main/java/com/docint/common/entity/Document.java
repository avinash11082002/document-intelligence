package com.docint.common.entity;

import com.docint.common.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * String UUID of the owning user — derived from JWT, never trusted from client.
     * Stored as String for backward DB compatibility; always set as userId.toString().
     */
    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "document_type")
    private String documentType;

    /** LLM-generated 2-4 sentence summary of the document. */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Page count extracted from Tika metadata (null if unavailable). */
    @Column(name = "page_count")
    private Integer pageCount;

    /** LLM-generated suggested starter questions for the document. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_questions", columnDefinition = "jsonb")
    private List<String> suggestedQuestions;

    /** Arbitrary key-value fields extracted by LLM classification. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb")
    private Map<String, Object> extractedFields;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
