package com.docint.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility for correlation ID propagation across HTTP, Kafka, and logs.
 */
public final class CorrelationIdUtil {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";

    private CorrelationIdUtil() {}

    public static String getOrGenerate() {
        String id = MDC.get(CORRELATION_ID_KEY);
        if (id == null || id.isBlank()) {
            id = generate();
            set(id);
        }
        return id;
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static void set(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }

    public static String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
