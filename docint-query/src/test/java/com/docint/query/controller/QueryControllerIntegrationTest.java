package com.docint.query.controller;

import com.docint.common.dto.QueryRequest;
import com.docint.common.dto.QueryResponse;
import com.docint.common.dto.SourceChunk;
import com.docint.query.exception.GlobalExceptionHandler;
import com.docint.query.security.AuthenticatedUser;
import com.docint.query.security.JwtAuthenticationFilter;
import com.docint.query.service.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class QueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QueryService queryService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AuthenticatedUser principal = new AuthenticatedUser(testUserId, "testuser", "test-token");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("End-to-End: POST /api/query returns grounded RAG answer with source citations")
    void query_endToEnd_returnsAnswerWithSources() throws Exception {
        UUID docId = UUID.randomUUID();
        QueryRequest request = new QueryRequest(docId, null, "What is the invoice total?");

        List<SourceChunk> sources = List.of(
                new SourceChunk(docId + "_0", docId, "invoice.pdf", "Total amount payable: $11,392.50 due Nov 23.", 0.95, 1)
        );

        QueryResponse response = new QueryResponse(
                "The total amount payable on the invoice is $11,392.50.",
                sources, false, "gemini", 1150L);

        when(queryService.query(any(), eq(testUserId))).thenReturn(response);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The total amount payable on the invoice is $11,392.50."))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.llmProvider").value("gemini"))
                .andExpect(jsonPath("$.sourceChunks[0].filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.sourceChunks[0].score").value(0.95));
    }
}

