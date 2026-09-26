package com.docint.common.exception;

public class LlmServiceUnavailableException extends RuntimeException {
    public LlmServiceUnavailableException(String message) {
        super(message);
    }

    public LlmServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
