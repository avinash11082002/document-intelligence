package com.docint.query.repository;

import com.docint.common.entity.MessageCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findByMessageId(UUID messageId);

    List<MessageCitation> findByMessageIdIn(List<UUID> messageIds);

    void deleteByMessageIdIn(List<UUID> messageIds);
}
