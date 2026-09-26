package com.docint.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A persistent conversation session.
 * Can be optionally scoped to a single document or span the entire workspace.
 */
@Entity
@Table(name = "conversations",
        indexes = {
                @Index(name = "idx_conversations_user_id", columnList = "user_id"),
                @Index(name = "idx_conversations_document_id", columnList = "document_id"),
                @Index(name = "idx_conversations_updated_at", columnList = "updated_at DESC")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owner of this conversation — derived from JWT, never from client. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Nullable: if set, retrieval is scoped to this document. */
    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
