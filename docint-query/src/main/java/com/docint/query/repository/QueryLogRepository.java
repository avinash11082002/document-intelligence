package com.docint.query.repository;

import com.docint.common.entity.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, UUID> {
}
