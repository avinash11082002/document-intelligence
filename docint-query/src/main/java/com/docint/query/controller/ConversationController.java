package com.docint.query.controller;

import com.docint.common.dto.conversation.ConversationResponse;
import com.docint.common.dto.conversation.CreateConversationRequest;
import com.docint.common.dto.conversation.MessageResponse;
import com.docint.common.dto.conversation.SendMessageRequest;
import com.docint.query.security.SecurityUtils;
import com.docint.query.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Conversations", description = "Multi-turn chat sessions with RAG citations, query rewriting, and SSE streaming")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @Operation(summary = "Create a new conversation session",
            description = "Creates a persistent chat session optionally scoped to a single document.")
    public ResponseEntity<ConversationResponse> createConversation(@Valid @RequestBody(required = false) CreateConversationRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        if (request == null) {
            request = new CreateConversationRequest(null, null);
        }
        ConversationResponse response = conversationService.createConversation(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List conversations", description = "Returns all conversations belonging to the authenticated user.")
    public ResponseEntity<List<ConversationResponse>> listConversations() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(conversationService.listConversations(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation details")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(conversationService.getConversation(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete conversation", description = "Deletes a conversation and all its messages.")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        conversationService.deleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Get conversation message history", description = "Returns all messages and attached citations in chronological order.")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(conversationService.getMessages(id, userId));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message in a conversation",
            description = "Submits a user message, performs conversational query rewriting, hybrid retrieval, LLM synthesis, and persists the answer and citations.")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        MessageResponse response = conversationService.sendMessage(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message with Server-Sent Events (SSE) streaming",
            description = "Streams status updates, citations, and answer tokens via SSE.")
    public SseEmitter streamMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout
        conversationService.streamMessage(id, request, userId, emitter);
        return emitter;
    }
}
