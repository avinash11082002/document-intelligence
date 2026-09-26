package com.docint.query.repository;

import com.docint.common.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<Message> findTop6ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    int countByConversationId(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
