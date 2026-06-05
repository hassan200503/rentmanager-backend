package com.rentmanager.shared.error;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ErrorEvent {

    private final UUID id;
    private final String traceId;

    private final UUID tenantId;
    private final UUID userId;

    private final String module;
    private final String errorType;
    private final String errorCode;

    private final String message;

    private final String path;
    private final String httpMethod;

    private final Map<String, Object> metadata;

    private final Instant timestamp;

    private ErrorEvent(Builder builder) {
        this.id = builder.id;
        this.traceId = builder.traceId;

        this.tenantId = builder.tenantId;
        this.userId = builder.userId;

        this.module = builder.module;
        this.errorType = builder.errorType;
        this.errorCode = builder.errorCode;
        this.message = builder.message;
        this.path = builder.path;
        this.httpMethod = builder.httpMethod;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp;
    }

    // =========================
    // GETTERS
    // =========================
    public UUID getId() { return id; }
    public String getTraceId() { return traceId; }

    public UUID getTenantId() { return tenantId; }

    public UUID getUserId() { return userId; }

    public String getModule() { return module; }
    public String getErrorType() { return errorType; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getHttpMethod() { return httpMethod; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }

    // =========================
    // FACTORY METHOD (FIXED LOCATION)
    // =========================
    public static ErrorEvent of(
            String traceId,
            UUID tenantId,
            UUID userId,
            String module,
            String errorType,
            String errorCode,
            String message,
            String path,
            String httpMethod,
            Map<String, Object> metadata
    ) {
        return new Builder()
                .id(UUID.randomUUID())
                .traceId(traceId)
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .module(module)
                .errorType(errorType)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .httpMethod(httpMethod)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
    }

    // =========================
    // BUILDER
    // =========================
    public static class Builder {
        private UUID id;
        private String traceId;
        private UUID tenantId;
        private UUID userId;
        private String module;
        private String errorType;
        private String errorCode;
        private String message;
        private String path;
        private String httpMethod;
        private Map<String, Object> metadata;
        private Instant timestamp;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }

        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }
        public Builder module(String module) { this.module = module; return this; }
        public Builder errorType(String errorType) { this.errorType = errorType; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public ErrorEvent build() {
            return new ErrorEvent(this);
        }
    }
}