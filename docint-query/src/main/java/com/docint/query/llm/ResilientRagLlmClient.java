package com.docint.query.llm;

import com.docint.common.exception.LlmServiceUnavailableException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j
@Component
public class ResilientRagLlmClient {

    private final ChatLanguageModel geminiModel;
    private final ChatLanguageModel groqModel;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${app.llm.gemini.timeout-seconds:10}")
    private int geminiTimeoutSeconds;

    @Value("${app.llm.groq.timeout-seconds:10}")
    private int groqTimeoutSeconds;

    public ResilientRagLlmClient(
            @Qualifier("geminiChatModel") ChatLanguageModel geminiModel,
            @Qualifier("groqChatModel") ChatLanguageModel groqModel,
            EmbeddingModel embeddingModel) {
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
        this.embeddingModel = embeddingModel;
    }

    @CircuitBreaker(name = "geminiLlm", fallbackMethod = "fallbackToGroq")
    @Retry(name = "geminiLlm")
    public RagLlmAnswer generateAnswer(String prompt) {
        log.debug("Generating answer via Gemini (wall-clock timeout: {}s)", geminiTimeoutSeconds);
        try {
            Future<String> future = executor.submit(() -> geminiModel.generate(prompt));
            String answer = future.get(geminiTimeoutSeconds, TimeUnit.SECONDS);
            return new RagLlmAnswer(answer, "gemini");
        } catch (TimeoutException e) {
            log.warn("Gemini generation exceeded {}s wall-clock limit. Falling back to Groq...", geminiTimeoutSeconds);
            throw new RuntimeException("Gemini timed out after " + geminiTimeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Gemini generation failed: {}", cause.getMessage());
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini generation interrupted", e);
        }
    }

    @CircuitBreaker(name = "groqLlm", fallbackMethod = "allProvidersDown")
    @Retry(name = "groqLlm")
    public RagLlmAnswer fallbackToGroq(String prompt, Throwable geminiEx) {
        log.warn("Gemini slow or failed ({}). Generating answer via Groq LPU (wall-clock timeout: {}s)...",
                geminiEx.getMessage(), groqTimeoutSeconds);
        try {
            Future<String> future = executor.submit(() -> groqModel.generate(prompt));
            String answer = future.get(groqTimeoutSeconds, TimeUnit.SECONDS);
            return new RagLlmAnswer(answer, "groq");
        } catch (TimeoutException e) {
            log.error("Groq generation exceeded {}s wall-clock limit", groqTimeoutSeconds);
            throw new RuntimeException("Groq timed out after " + groqTimeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Groq generation failed: {}", cause.getMessage());
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Groq generation interrupted", e);
        }
    }

    public RagLlmAnswer allProvidersDown(String prompt, Throwable ex) {
        log.error("All RAG LLM providers failed: {}", ex.getMessage());
        throw new LlmServiceUnavailableException("All LLM providers are currently unavailable or timed out", ex);
    }

    @Retry(name = "geminiLlm")
    public float[] embedQuery(String question) {
        return embeddingModel.embed(question).content().vector();
    }

    public record RagLlmAnswer(String answer, String provider) {}
}

