package com.docint.query.controller;

import com.docint.common.dto.QueryRequest;
import com.docint.common.dto.QueryResponse;
import com.docint.query.security.SecurityUtils;
import com.docint.query.service.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "RAG Query", description = "Natural language question-answering with hybrid BM25 + k-NN retrieval")
public class QueryController {

    private final QueryService queryService;

    @PostMapping
    @Operation(summary = "Ask a question over your documents",
            description = "Performs hybrid retrieval and synthesizes a grounded answer. " +
                          "ownerId is derived from the JWT — never from the request body.")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        QueryResponse response = queryService.query(request, userId);
        return ResponseEntity.ok(response);
    }
}
