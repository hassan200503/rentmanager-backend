package com.rentmanager.modules.integration.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request/response shapes for the Integrations control plane API.
 * Secret values are never present in any response — only masked previews
 * and configured flags.
 */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public record FieldView(
            String key,
            String label,
            boolean secret,
            boolean configured,
            String value,
            String placeholder
    ) {}

    public record TestTargetView(
            String kind,
            boolean required,
            String label,
            String message
    ) {}

    public record EnvironmentView(
            String environment,
            boolean active,
            String status,
            boolean configured,
            Instant lastVerifiedAt,
            String lastVerifiedBy,
            String lastError,
            String updatedBy,
            Instant updatedAt,
            List<FieldView> fields
    ) {}

    public record ProviderView(
            String providerKey,
            String displayName,
            String category,
            String docsUrl,
            boolean supportsTestConnection,
            TestTargetView testTarget,
            List<EnvironmentView> environments
    ) {}

    public record UpdateCredentialsRequest(Map<String, String> credentials) {}

    public record TestConnectionRequest(String target) {}

    public record TestConnectionView(
            boolean ok,
            String message,
            String error,
            String status
    ) {}

    public record AuditEntryView(
            String environment,
            String action,
            String actorUserId,
            Map<String, Object> metadata,
            String ipAddress,
            Instant createdAt
    ) {}

    public record ActivateResultView(EnvironmentView environment) {}

    public record RolloutSkipView(String providerKey, String displayName, String reason) {}

    public record RolloutView(
            String targetEnvironment,
            List<String> activated,
            List<RolloutSkipView> skipped
    ) {}
}