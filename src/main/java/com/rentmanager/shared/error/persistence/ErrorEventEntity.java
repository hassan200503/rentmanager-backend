package com.rentmanager.shared.error.persistence;

import com.rentmanager.shared.error.ErrorEvent;
import com.rentmanager.shared.util.JsonUtil;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "error_events")
public class ErrorEventEntity {

    @Id
    private UUID id;

    private String traceId;

    private UUID tenantId;

    private UUID userId;

    private String module;

    private String errorType;

    private String errorCode;

    @Column(length = 2000)
    private String message;

    private String path;

    private String httpMethod;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    private Instant timestamp;

    public ErrorEventEntity() {}

    // =========================
    // Mapping (Domain → Entity)
    // =========================
    public static ErrorEventEntity from(ErrorEvent event) {
        ErrorEventEntity e = new ErrorEventEntity();

        e.id = event.getId();
        e.traceId = event.getTraceId();
        e.tenantId = event.getTenantId();
        e.userId = event.getUserId();
        e.module = event.getModule();
        e.errorType = event.getErrorType();
        e.errorCode = event.getErrorCode();
        e.message = event.getMessage();
        e.path = event.getPath();
        e.httpMethod = event.getHttpMethod();
        e.metadataJson = JsonUtil.toJson(event.getMetadata());
        e.timestamp = event.getTimestamp();

        return e;
    }

    // =========================
    // Getters (required for JPA + mapping safety)
    // =========================

    public UUID getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public UUID  getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getModule() {
        return module;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // =========================
    // Optional setters (only if JPA or future updates need them)
    // =========================

    public void setId(UUID id) {
        this.id = id;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}