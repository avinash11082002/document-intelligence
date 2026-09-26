package com.docint.query.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfig {

    @Value("${app.llm.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.llm.gemini.chat-model:gemini-1.5-flash}")
    private String geminiChatModel;

    @Value("${app.llm.gemini.embedding-model:gemini-embedding-2-preview}")
    private String geminiEmbeddingModel;

    @Value("${app.llm.gemini.embedding-dimensions:768}")
    private int embeddingDimensions;

    @Value("${app.llm.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.llm.groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${app.llm.groq.model:openai/gpt-oss-20b}")
    private String groqModel;

    @Bean("geminiChatModel")
    @Primary
    public ChatLanguageModel geminiChatModel() {
        String key = (geminiApiKey != null && !geminiApiKey.isBlank()) ? geminiApiKey : "placeholder-api-key";
        return GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName(geminiChatModel)
                .temperature(0.2)
                .maxOutputTokens(4096)
                .timeout(java.time.Duration.ofSeconds(15)) // fallback to Groq after 15s
                .build();
    }

    @Bean("groqChatModel")
    public ChatLanguageModel groqChatModel() {
        String key = (groqApiKey != null && !groqApiKey.isBlank()) ? groqApiKey : "placeholder-api-key";
        return OpenAiChatModel.builder()
                .baseUrl(groqBaseUrl)
                .apiKey(key)
                .modelName(groqModel)
                .temperature(0.2)
                .maxTokens(4096)
                .timeout(java.time.Duration.ofSeconds(10)) // hard limit on Groq
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        String key = (geminiApiKey != null && !geminiApiKey.isBlank()) ? geminiApiKey : "placeholder-api-key";
        return GoogleAiEmbeddingModel.builder()
                .apiKey(key)
                .modelName(geminiEmbeddingModel)
                .outputDimensionality(embeddingDimensions)
                .build();
    }
}
