package com.docint.api.repository;

import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByContentHashAndOwnerId(String contentHash, String ownerId);

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndStatus(String ownerId, DocumentStatus status, Pageable pageable);

    /** Full-text search over filename and document type. */
    @Query("SELECT d FROM Document d WHERE d.ownerId = :ownerId AND " +
           "(LOWER(d.filename) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.documentType) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Document> searchByOwnerIdAndQuery(@Param("ownerId") String ownerId,
                                            @Param("query") String query);
}
