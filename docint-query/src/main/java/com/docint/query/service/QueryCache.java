package com.docint.query.service;

import com.docint.common.dto.QueryResponse;

import java.util.Optional;
import java.util.UUID;

public interface QueryCache {

    Optional<QueryResponse> get(String ownerId, UUID documentId, String question);

    void put(String ownerId, UUID documentId, String question, QueryResponse response);

    void evict(String ownerId, UUID documentId);
}
