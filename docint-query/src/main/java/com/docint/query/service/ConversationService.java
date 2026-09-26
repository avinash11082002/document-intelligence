package com.docint.query.service;

import com.docint.common.dto.conversation.ConversationResponse;
import com.docint.common.dto.conversation.CreateConversationRequest;
import com.docint.common.dto.conversation.MessageResponse;
import com.docint.common.dto.conversation.SendMessageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

public interface ConversationService {

    ConversationResponse createConversation(CreateConversationRequest request, UUID userId);

    List<ConversationResponse> listConversations(UUID userId);

    ConversationResponse getConversation(UUID conversationId, UUID userId);

    void deleteConversation(UUID conversationId, UUID userId);

    List<MessageResponse> getMessages(UUID conversationId, UUID userId);

    MessageResponse sendMessage(UUID conversationId, SendMessageRequest request, UUID userId);

    void streamMessage(UUID conversationId, SendMessageRequest request, UUID userId, SseEmitter emitter);
}
