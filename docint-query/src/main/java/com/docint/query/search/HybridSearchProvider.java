package com.docint.query.search;

import com.docint.common.dto.SourceChunk;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface HybridSearchProvider {

    List<SourceChunk> search(String queryText, float[] queryEmbedding, String ownerId, UUID documentId) throws IOException;
}
