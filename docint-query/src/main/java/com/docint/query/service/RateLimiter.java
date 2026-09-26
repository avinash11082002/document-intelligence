package com.docint.query.service;

public interface RateLimiter {

    void checkRateLimit(String ownerId);
}
