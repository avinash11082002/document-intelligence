package com.docint.query.service.impl;

import com.docint.common.exception.RateLimitExceededException;
import com.docint.query.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.cache.rate-limit-window-seconds:60}")
    private int windowSeconds;

    @Value("${app.cache.rate-limit-max-requests:60}")
    private int maxRequests;

    @Override
    public void checkRateLimit(String ownerId) {
        String key = "ratelimit:" + ownerId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > maxRequests) {
                log.warn("Rate limit breached for owner={}: {}/{} requests", ownerId, count, maxRequests);
                throw new RateLimitExceededException(ownerId);
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Fail open if Redis is unreachable
            log.warn("Rate limit check failed (failing open): {}", e.getMessage());
        }
    }
}
