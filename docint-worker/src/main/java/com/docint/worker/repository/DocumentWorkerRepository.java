package com.docint.worker.repository;

import com.docint.common.entity.Document;
import com.docint.common.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentWorkerRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);
}
