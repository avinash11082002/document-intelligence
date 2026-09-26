package com.docint.common.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String ownerId) {
        super("Rate limit exceeded for owner: " + ownerId);
    }
}
