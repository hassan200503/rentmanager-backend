package com.rentmanager.modules.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.integration.api.dto.IntegrationDtos;
import com.rentmanager.modules.integration.application.provider.ProviderTester;
import com.rentmanager.modules.integration.application.provider.ProviderTesterRegistry;
import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.IntegrationStatus;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import com.rentmanager.modules.integration.domain.model.ProviderDefinition;
import com.rentmanager.modules.integration.domain.repository.IntegrationConfigRepository;
import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationAuditLogEntity;
import com.rentmanager.modules.integration.infrastructure.persistence.repository.IntegrationAuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backing service for the Integrations control plane: listing providers,
 * saving per-environment credentials (encrypted), activating an
 * environment, running real Test Connections, and appending to the
 * append-only audit trail. Every mutation writes an audit entry with
 * redacted, field-level diffs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationAdminService {

    private final IntegrationConfigRepository repository;
    private final IntegrationEncryptionService encryptionService;
    private final IntegrationRegistry registry;
    private final ProviderTesterRegistry testerRegistry;
    private final IntegrationAuditLogJpaRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<IntegrationDtos.ProviderView> list() {
        return ProviderCatalog.all().stream()
                .map(def -> view(def, List.of(IntegrationEnvironment.values())))
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationDtos.ProviderView get(String providerKey) {
        ProviderDefinition definition = ProviderCatalog.get(providerKey);
        return view(definition, List.of(IntegrationEnvironment.values()));
    }

    @Transactional(readOnly = true)
    public List<IntegrationDtos.AuditEntryView> audit(String providerKey, int limit) {
        ProviderCatalog.get(providerKey); // validates the key
        int capped = Math.min(Math.max(limit, 1), 100);
        return auditLogRepository.findTop50ByProviderKeyOrderByCreatedAtDesc(providerKey)
                .stream()
                .limit(capped)
                .map(e -> new IntegrationDtos.AuditEntryView(
                        e.getEnvironment(),
                        e.getAction(),
                        e.getActorUserId(),
                        parseMetadata(e.getMetadata()),
                        e.getIpAddress(),
                        e.getCreatedAt()))
                .toList();
    }

    /**
     * Upsert credentials for one environment. Secret fields left blank in
     * the request keep their previously stored value (so the owner can edit
     * non-secret fields without re-entering secrets). Unknown field keys
     * are dropped; a payload with nothing non-blank is a no-op.
     */
    @Transactional
    public IntegrationDtos.ProviderView update(
            String providerKey,
            IntegrationEnvironment environment,
            Map<String, String> incoming,
            String actor,
            String ipAddress
    ) {
        ProviderDefinition definition = ProviderCatalog.get(providerKey);
        IntegrationConfig config = repository.find(providerKey, environment)
                .orElseGet(() -> IntegrationConfig.newUnconfigured(providerKey, environment));

        Map<String, String> merged = new LinkedHashMap<>();
        for (var field : definition.fields()) {
            String newValue = incoming.getOrDefault(field.key(), "");
            if (definition.isSecret(field.key()) && isBlank(newValue) && config.hasCredentials()) {
                Map<String, String> existing = decrypt(config.getEncryptedCredentials());
                newValue = existing.getOrDefault(field.key(), "");
            }
            if (newValue == null) {
                newValue = "";
            }
            merged.put(field.key(), newValue.trim());
        }

        if (config.hasCredentials()) {
            Map<String, String> before = decrypt(config.getEncryptedCredentials());
            Map<String, Object> diff = SecretRedactor.redactedDiff(
                    before, merged, key -> definition.isSecret(key));
            audit(providerKey, environment, "updated", actor, ipAddress,
                    Map.of("fields", diff, "environment", environment.name()));
        } else {
            audit(providerKey, environment, "created", actor, ipAddress,
                    Map.of("fields", "initial", "environment", environment.name()));
        }

        config.replaceCredentials(
                encryptionService.encryptPayload(toJson(merged)),
                config.getKeyVersion(),
                actor);
        config.touchUpdatedAt();
        repository.save(config);
        registry.invalidate(providerKey);

        log.info("Integration credentials updated: provider={} env={} actor={}",
                providerKey, environment, actor);
        return get(providerKey);
    }

    /**
     * Activates one environment (deactivates the sibling). Production
     * activation requires a VERIFIED status — the owner must have passed a
     * real Test Connection after the last credential change.
     */
    @Transactional
    public IntegrationDtos.ActivateResultView activate(
            String providerKey,
            IntegrationEnvironment environment,
            String actor,
            String ipAddress
    ) {
        ProviderCatalog.get(providerKey);
        IntegrationConfig config = repository.find(providerKey, environment)
                .orElseThrow(() -> new IllegalStateException(
                        "Nothing saved for " + providerKey + " " + environment + " — save credentials first."));

        if (environment == IntegrationEnvironment.PRODUCTION
                && config.getStatus() != IntegrationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Production cannot be activated until Test Connection has passed for these "
                            + "credentials (status is " + config.getStatus() + ").");
        }

        repository.deactivateAll(providerKey, environment);
        config.activate();
        config.touchUpdatedAt();
        repository.save(config);
        registry.invalidate(providerKey);

        audit(providerKey, environment, "activated", actor, ipAddress,
                Map.of("environment", environment.name()));
        log.info("Integration activated: provider={} env={} actor={}", providerKey, environment, actor);
        return new IntegrationDtos.ActivateResultView(environmentView(config));
    }

    /**
     * Runs the real provider test against the saved credentials of the
     * given environment and records the outcome in the config status.
     */
    @Transactional
    public IntegrationDtos.TestConnectionView test(
            String providerKey,
            IntegrationEnvironment environment,
            String target,
            String actor,
            String ipAddress
    ) {
        ProviderDefinition definition = ProviderCatalog.get(providerKey);
        IntegrationConfig config = repository.find(providerKey, environment)
                .orElseGet(() -> IntegrationConfig.newUnconfigured(providerKey, environment));

        Map<String, String> credentials = registry.resolveSaved(providerKey, environment);

        ProviderTester tester = testerRegistry.get(providerKey);
        ProviderTester.TestResult result;
        try {
            result = tester.test(credentials, blankToNull(target), null);
        } catch (Exception e) {
            log.warn("Integration test crashed for provider={} env={}", providerKey, environment, e);
            result = ProviderTester.TestResult.failure(
                    "Test connection crashed", String.valueOf(e.getMessage()));
        }

        if (result.ok()) {
            config.markVerified(actor != null ? actor : "system");
        } else {
            config.markError(result.error() != null ? result.error() : result.message());
        }
        config.touchUpdatedAt();
        repository.save(config);
        registry.invalidate(providerKey);

        audit(providerKey, environment, "tested", actor, ipAddress,
                Map.of("ok", result.ok(), "message", result.message(), "provider", definition.displayName()));
        log.info("Integration test: provider={} env={} ok={} message={}",
                providerKey, environment, result.ok(), result.message().substring(0, Math.min(result.message().length(), 200)));

        return new IntegrationDtos.TestConnectionView(
                result.ok(),
                result.message(),
                result.error(),
                config.getStatus().name());
    }

    // ---------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------

    private IntegrationDtos.ProviderView view(ProviderDefinition definition,
                                              List<IntegrationEnvironment> environments) {
        List<IntegrationDtos.EnvironmentView> envViews = new ArrayList<>();
        for (IntegrationEnvironment environment : environments) {
            Optional<IntegrationConfig> saved = repository.find(definition.key(), environment);
            if (saved.isPresent()) {
                envViews.add(environmentView(saved.get()));
            } else {
                envViews.add(unconfiguredView(definition, environment));
            }
        }
        return new IntegrationDtos.ProviderView(
                definition.key(),
                definition.displayName(),
                definition.category(),
                definition.docsUrl(),
                definition.supportsTestConnection(),
                envViews);
    }

    private IntegrationDtos.EnvironmentView environmentView(IntegrationConfig config) {
        ProviderDefinition definition = ProviderCatalog.get(config.getProviderKey());
        Map<String, String> decrypted = config.hasCredentials()
                ? decrypt(config.getEncryptedCredentials())
                : Map.of();

        List<IntegrationDtos.FieldView> fields = definition.fields().stream()
                .map(field -> {
                    String value = decrypted.getOrDefault(field.key(), "");
                    boolean configured = !value.isBlank();
                    String shown = field.secret()
                            ? (configured ? SecretRedactor.mask(value) : "")
                            : value;
                    return new IntegrationDtos.FieldView(
                            field.key(), field.label(), field.secret(),
                            configured, shown, field.placeholder());
                })
                .toList();

        return new IntegrationDtos.EnvironmentView(
                config.getEnvironment().name(),
                config.isActive(),
                config.getStatus().name(),
                config.hasCredentials(),
                config.getLastVerifiedAt(),
                config.getLastVerifiedBy(),
                config.getLastError(),
                config.getUpdatedBy(),
                config.getUpdatedAt(),
                fields);
    }

    private IntegrationDtos.EnvironmentView unconfiguredView(ProviderDefinition definition,
                                                             IntegrationEnvironment environment) {
        List<IntegrationDtos.FieldView> fields = definition.fields().stream()
                .map(field -> new IntegrationDtos.FieldView(
                        field.key(), field.label(), field.secret(), false, "", field.placeholder()))
                .toList();
        return new IntegrationDtos.EnvironmentView(
                environment.name(), false, "NOT_CONFIGURED", false,
                null, null, null, null, null, fields);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Map<String, String> decrypt(String encrypted) {
        try {
            String json = encryptionService.decryptPayload(encrypted);
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt stored credentials for display — check the encryption key is unchanged", e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize credentials payload", e);
        }
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", metadata);
        }
    }

    private void audit(String providerKey, IntegrationEnvironment environment, String action,
                       String actor, String ipAddress, Map<String, Object> metadata) {
        try {
            auditLogRepository.save(new IntegrationAuditLogEntity(
                    providerKey,
                    environment.name(),
                    action,
                    actor == null || actor.isBlank() ? "platform-owner" : actor,
                    toJson(metadata),
                    ipAddress
            ));
        } catch (Exception e) {
            // Audit must never block the configuration write.
            log.warn("Failed to record integration audit event. provider={} action={}", providerKey, action, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}