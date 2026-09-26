package com.docint.worker.llm;

import java.util.List;

public interface EmbeddingLlmClient {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
