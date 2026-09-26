package com.docint.worker.llm.impl;

import com.docint.common.exception.LlmServiceUnavailableException;
import com.docint.worker.llm.ChatLlmClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Resilient Chat LLM Client implementing Gemini (primary) -> Groq (fallback) chain
 * guarded with Resilience4j circuit breakers and retries.
 */
@Slf4j
@Component
public class ResilientChatLlmClient implements ChatLlmClient {

    private final ChatLanguageModel geminiModel;
    private final ChatLanguageModel groqModel;

    public ResilientChatLlmClient(
            @Qualifier("geminiChatModel") ChatLanguageModel geminiModel,
            @Qualifier("groqChatModel") ChatLanguageModel groqModel) {
        this.geminiModel = geminiModel;
        this.groqModel = groqModel;
    }

    @Override
    @CircuitBreaker(name = "geminiLlm", fallbackMethod = "fallbackToGroq")
    @Retry(name = "geminiLlm")
    public LlmChatResult chat(String prompt) {
        log.debug("Invoking primary LLM (Gemini)");
        String reply = geminiModel.generate(prompt);
        return new LlmChatResult(reply, "gemini");
    }

    @CircuitBreaker(name = "groqLlm", fallbackMethod = "allLlmFailed")
    @Retry(name = "groqLlm")
    public LlmChatResult fallbackToGroq(String prompt, Throwable error) {
        log.warn("Gemini unavailable ({}). Falling back to Groq LPU...", error.getMessage());
        String reply = groqModel.generate(prompt);
        return new LlmChatResult(reply, "groq");
    }

    public LlmChatResult allLlmFailed(String prompt, Throwable error) {
        log.error("All LLM providers exhausted: {}", error.getMessage());
        throw new LlmServiceUnavailableException("All configured LLM providers failed", error);
    }
}
