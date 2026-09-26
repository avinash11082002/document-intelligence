package com.docint.query.repository;

import com.docint.common.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentQueryRepository extends JpaRepository<Document, UUID> {
}
