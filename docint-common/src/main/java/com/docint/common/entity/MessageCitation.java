package com.docint.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A citation attached to an ASSISTANT message.
 * References the specific chunk in Elasticsearch that supported the answer.
 */
@Entity
@Table(name = "message_citations",
        indexes = {
                @Index(name = "idx_citations_message_id", columnList = "message_id"),
                @Index(name = "idx_citations_document_id", columnList = "document_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false, length = 512)
    private String filename;

    /** Elasticsearch chunk ID (documentId_chunkIndex). */
    @Column(name = "chunk_id", nullable = false, length = 256)
    private String chunkId;

    @Column(name = "page_number")
    private Integer pageNumber;

    /** Short quoted snippet from the source chunk (max 300 chars). */
    @Column(columnDefinition = "TEXT")
    private String snippet;

    /** Normalised retrieval score for display (0.0 – 1.0). */
    @Column(name = "score")
    private Double score;
}
