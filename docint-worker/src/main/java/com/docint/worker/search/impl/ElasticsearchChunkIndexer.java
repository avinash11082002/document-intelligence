package com.docint.worker.search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.docint.common.entity.Document;
import com.docint.worker.chunker.TextChunker;
import com.docint.worker.search.ChunkIndexer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchChunkIndexer implements ChunkIndexer {

    private final ElasticsearchClient esClient;

    @Value("${app.search.index-name:document_chunks}")
    private String indexName;

    @Value("${app.llm.gemini.embedding-dimensions:768}")
    private int embeddingDimensions;

    @PostConstruct
    public void ensureIndexExists() {
        try {
            boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (!exists) {
                createIndex();
                log.info("Initialized Elasticsearch index '{}' with {} vector dims", indexName, embeddingDimensions);
            }
        } catch (Exception e) {
            log.warn("Could not verify Elasticsearch index existence: {}", e.getMessage());
        }
    }

    private void createIndex() throws IOException {
        String mapping = """
                {
                  "mappings": {
                    "properties": {
                      "chunk_id":        { "type": "keyword" },
                      "document_id":     { "type": "keyword" },
                      "owner_id":        { "type": "keyword" },
                      "chunk_index":     { "type": "integer" },
                      "content":         { "type": "text", "analyzer": "standard" },
                      "document_type":   { "type": "keyword" },
                      "filename":        { "type": "keyword" },
                      "embedding": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      },
                      "metadata": {
                        "type": "object",
                        "properties": {
                          "page_number":   { "type": "integer" },
                          "char_offset":   { "type": "integer" }
                        }
                      },
                      "created_at":      { "type": "date" }
                    }
                  },
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  }
                }
                """.formatted(embeddingDimensions);

        esClient.indices().create(CreateIndexRequest.of(c -> c.index(indexName).withJson(new StringReader(mapping))));
    }

    @Override
    public void indexChunks(Document document, List<TextChunker.Chunk> chunks, List<float[]> embeddings) throws IOException {
        deleteChunksForDocument(document.getId());

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.Chunk chunk = chunks.get(i);
            float[] embedding = embeddings.get(i);
            String chunkId = document.getId() + "_" + chunk.getIndex();

            Map<String, Object> docMap = new LinkedHashMap<>();
            docMap.put("chunk_id", chunkId);
            docMap.put("document_id", document.getId().toString());
            docMap.put("owner_id", document.getOwnerId());
            docMap.put("chunk_index", chunk.getIndex());
            docMap.put("content", chunk.getContent());
            docMap.put("document_type", document.getDocumentType());
            docMap.put("filename", document.getFilename());
            docMap.put("embedding", toFloatList(embedding));
            docMap.put("metadata", Map.of(
                    "char_offset", chunk.getCharOffset(),
                    "page_number", chunk.getPageNumber() != null ? chunk.getPageNumber() : 0
            ));
            docMap.put("created_at", Instant.now().toString());

            bulkBuilder.operations(op -> op
                    .index(ix -> ix.index(indexName).id(chunkId).document(docMap)));
        }

        BulkResponse response = esClient.bulk(bulkBuilder.build());
        if (response.errors()) {
            String errorReason = response.items().stream()
                    .filter(it -> it.error() != null)
                    .map(it -> it.error().reason())
                    .findFirst()
                    .orElse("Unknown indexing error");
            throw new IOException("Bulk indexing failed: " + errorReason);
        }

        log.info("Indexed {} chunks for document {} into ES index '{}'",
                chunks.size(), document.getId(), indexName);
    }

    @Override
    public void deleteChunksForDocument(UUID documentId) {
        try {
            esClient.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("document_id").value(documentId.toString()))));
            log.debug("Cleared previous index entries for document {}", documentId);
        } catch (Exception ignored) {
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
