package com.rentmanager.modules.audit.infrastructure.config;

import java.util.UUID;

public class CorrelationContext {

    private static final ThreadLocal<String> correlationIdHolder = new ThreadLocal<>();

    public static void setCorrelationId(String correlationId) {
        correlationIdHolder.set(correlationId);
    }

    public static String getCorrelationId() {
        return correlationIdHolder.get();
    }

    public static void clear() {
        correlationIdHolder.remove();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}