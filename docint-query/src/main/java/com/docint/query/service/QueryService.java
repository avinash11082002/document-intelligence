package com.docint.query.service;

import com.docint.common.dto.QueryRequest;
import com.docint.common.dto.QueryResponse;

import java.util.UUID;

public interface QueryService {

    /**
     * Execute a RAG query for the authenticated user.
     * @param request question + optional document scope
     * @param userId  authenticated user from JWT (never from request body)
     */
    QueryResponse query(QueryRequest request, UUID userId);
}
