package com.docint.worker.llm.impl;

import com.docint.worker.llm.EmbeddingLlmClient;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GeminiEmbeddingLlmClient implements EmbeddingLlmClient {

    private final EmbeddingModel primaryModel;
    private final EmbeddingModel fallbackModel;

    // Gemini Free Tier limit is 15 RPM.
    // Batch size of 25 chunks reduces 972 chunks to 39 requests.
    // Inter-batch delay of 6s ensures ~8-9 RPM, well under the 15 RPM ceiling.
    private static final int BATCH_SIZE = 25;
    private static final long INTER_BATCH_DELAY_MS = 6000;

    public GeminiEmbeddingLlmClient(
            @Qualifier("primaryEmbeddingModel") EmbeddingModel primaryModel,
            @Qualifier("fallbackEmbeddingModel") EmbeddingModel fallbackModel) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    @Override
    @Retry(name = "geminiLlm")
    public float[] embed(String text) {
        try {
            var embedding = primaryModel.embed(text);
            return embedding.content().vector();
        } catch (Exception e) {
            log.warn("Primary embedding failed ({}), falling back to secondary embedding model...", e.getMessage());
            var embedding = fallbackModel.embed(text);
            return embedding.content().vector();
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>(texts.size());
        int totalBatches = (int) Math.ceil((double) texts.size() / BATCH_SIZE);

        log.info("Starting batched embedding: {} chunks across {} batches (batchSize={})",
                texts.size(), totalBatches, BATCH_SIZE);

        for (int b = 0; b < totalBatches; b++) {
            int start = b * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, texts.size());
            List<String> subList = texts.subList(start, end);
            List<TextSegment> segments = subList.stream().map(TextSegment::from).toList();

            List<float[]> batchVectors = embedBatchWithRetry(segments, b + 1, totalBatches);
            allEmbeddings.addAll(batchVectors);

            if (b < totalBatches - 1) {
                try {
                    TimeUnit.MILLISECONDS.sleep(INTER_BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding process interrupted", e);
                }
            }
        }

        log.info("Successfully completed batched embedding: {} vectors generated", allEmbeddings.size());
        return allEmbeddings;
    }

    private static final Object GLOBAL_EMBEDDING_LOCK = new Object();

    private List<float[]> embedBatchWithRetry(List<TextSegment> segments, int batchNum, int totalBatches) {
        int maxAttempts = 6;
        long waitTimeMs = 15000;
        EmbeddingModel currentModel = primaryModel;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response<List<Embedding>> response;
                synchronized (GLOBAL_EMBEDDING_LOCK) {
                    response = currentModel.embedAll(segments);
                }
                log.debug("Embedded batch {}/{} ({} segments)", batchNum, totalBatches, segments.size());
                return response.content().stream().map(Embedding::vector).toList();
            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null &&
                        (e.getMessage().contains("429") || e.getMessage().contains("RESOURCE_EXHAUSTED") || e.getMessage().contains("quota"));

                if (isRateLimit && attempt < maxAttempts) {
                    long sleepMs = waitTimeMs;
                    if (e.getMessage() != null) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s").matcher(e.getMessage());
                        if (m.find()) {
                            try {
                                double seconds = Double.parseDouble(m.group(1));
                                sleepMs = Math.max(sleepMs, (long) Math.ceil((seconds + 2.0) * 1000));
                            } catch (Exception ignored) {}
                        }
                    }
                    log.warn("Rate limit on batch {}/{} (attempt {}/{}). Waiting {}ms before retry...",
                            batchNum, totalBatches, attempt, maxAttempts, sleepMs);
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during rate limit backoff", ie);
                    }
                    waitTimeMs = Math.min(waitTimeMs * 2, 60000);
                } else if (currentModel == primaryModel && fallbackModel != null) {
                    log.warn("Primary embedding exhausted retries on batch {}/{}. Attempting fallback model...", batchNum, totalBatches);
                    currentModel = fallbackModel;
                    attempt = 0; // restart attempts on fallback
                } else {
                    log.error("Batch {}/{} failed on attempt {}: {}", batchNum, totalBatches, attempt, e.getMessage());
                    throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
                }
            }
        }
        throw new RuntimeException("Exhausted retries for batch " + batchNum);
    }
}
