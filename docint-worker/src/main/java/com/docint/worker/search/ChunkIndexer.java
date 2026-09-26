package com.docint.worker.search;

import com.docint.common.entity.Document;
import com.docint.worker.chunker.TextChunker;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface ChunkIndexer {

    void indexChunks(Document document, List<TextChunker.Chunk> chunks, List<float[]> embeddings) throws IOException;

    void deleteChunksForDocument(UUID documentId) throws IOException;
}
