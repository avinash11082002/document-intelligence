package com.docint.query.service.impl;

import com.docint.common.dto.QueryResponse;
import com.docint.common.util.HashUtil;
import com.docint.query.service.QueryCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueryCache implements QueryCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.rag-answer-ttl-minutes:30}")
    private int scopedTtlMinutes;

    @Value("${app.cache.rag-cross-doc-ttl-minutes:15}")
    private int crossDocTtlMinutes;

    @Override
    public Optional<QueryResponse> get(String ownerId, UUID documentId, String question) {
        String key = cacheKey(ownerId, documentId, question);
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                log.debug("Redis cache HIT: {}", key);
                return Optional.of(objectMapper.readValue(val, QueryResponse.class));
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void put(String ownerId, UUID documentId, String question, QueryResponse response) {
        String key = cacheKey(ownerId, documentId, question);
        int ttl = (documentId != null) ? scopedTtlMinutes : crossDocTtlMinutes;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttl));
            log.debug("Cached RAG answer (TTL={}m) -> {}", ttl, key);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize RAG answer for cache: {}", e.getMessage());
        }
    }

    @Override
    public void evict(String ownerId, UUID documentId) {
        String scoped = "rag:answer:" + ownerId + ":" + documentId + ":*";
        String cross = "rag:answer:all:" + ownerId + ":*";
        evictByPattern(scoped);
        evictByPattern(cross);
    }

    private void evictByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} cache keys matching '{}'", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to evict pattern {}: {}", pattern, e.getMessage());
        }
    }

    private String cacheKey(String ownerId, UUID documentId, String question) {
        String hash = HashUtil.hashQuestion(question);
        return (documentId != null)
                ? "rag:answer:" + ownerId + ":" + documentId + ":" + hash
                : "rag:answer:all:" + ownerId + ":" + hash;
    }
}
