package com.docint.query.search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.docint.common.dto.SourceChunk;
import com.docint.query.search.HybridSearchProvider;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchHybridSearchProvider implements HybridSearchProvider {

    private final ElasticsearchClient esClient;

    @Value("${app.search.index-name:document_chunks}")
    private String indexName;

    @Value("${app.search.top-k:5}")
    private int topK;

    @Value("${app.search.knn-weight:0.7}")
    private double knnWeight;

    @Override
    @Retry(name = "elasticsearch")
    public List<SourceChunk> search(String queryText, float[] queryEmbedding, String ownerId, UUID documentId) throws IOException {
        log.debug("Hybrid query: '{}' [owner={}, docId={}]", queryText, ownerId, documentId);

        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("owner_id").value(ownerId))));
        if (documentId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("document_id").value(documentId.toString()))));
        }

        // BM25 Text Query
        Query textQuery = Query.of(q -> q.bool(BoolQuery.of(b -> b
                .must(Query.of(m -> m.match(mt -> mt.field("content").query(queryText))))
                .filter(filters)
        )));

        // kNN Vector Query
        List<Float> vector = new ArrayList<>(queryEmbedding.length);
        for (float f : queryEmbedding) vector.add(f);

        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .size(topK)
                .query(textQuery)
                .knn(knn -> knn
                        .field("embedding")
                        .queryVector(vector)
                        .k(topK)
                        .numCandidates(topK * 10)
                        .filter(filters)
                        .boost((float) knnWeight)
                )
        );

        SearchResponse<Map> response = esClient.search(request, Map.class);
        List<SourceChunk> chunks = new ArrayList<>();

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> src = hit.source();
            if (src == null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) src.getOrDefault("metadata", Map.of());

            chunks.add(new SourceChunk(
                    (String) src.get("chunk_id"),
                    UUID.fromString((String) src.get("document_id")),
                    (String) src.get("filename"),
                    (String) src.get("content"),
                    hit.score() != null ? hit.score() : 0.0,
                    meta.get("page_number") instanceof Number n ? n.intValue() : null
            ));
        }

        log.info("Hybrid search retrieved {} relevant chunks for query: '{}'", chunks.size(), queryText);
        return chunks;
    }
}
