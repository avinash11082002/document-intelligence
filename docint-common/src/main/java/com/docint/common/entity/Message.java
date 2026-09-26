package com.docint.common.entity;

import com.docint.common.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single turn in a Conversation.
 * USER messages store the original question as typed.
 * ASSISTANT messages store the grounded RAG answer.
 */
@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_id", columnList = "conversation_id"),
                @Index(name = "idx_messages_created_at", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    /** The message content displayed to the user. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * For ASSISTANT messages: the rewritten retrieval query
     * (original user question may be different — kept for display/audit).
     */
    @Column(name = "rewritten_query", columnDefinition = "TEXT")
    private String rewrittenQuery;

    /** Milliseconds from request receipt to response completion. */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /** Which LLM provider generated this response (gemini / groq / none). */
    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
