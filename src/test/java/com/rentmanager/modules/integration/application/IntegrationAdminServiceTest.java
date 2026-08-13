package com.rentmanager.modules.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.integration.api.dto.IntegrationDtos;
import com.rentmanager.modules.integration.application.provider.ProviderTester;
import com.rentmanager.modules.integration.application.provider.ProviderTesterRegistry;
import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.IntegrationStatus;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import com.rentmanager.modules.integration.domain.repository.IntegrationConfigRepository;
import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationAuditLogEntity;
import com.rentmanager.modules.integration.infrastructure.persistence.repository.IntegrationAuditLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integrations control plane: encrypted upserts with secret preservation,
 * redacted audit diffs, per-environment activation guards (Production needs
 * a passed Test Connection), and real Test Connection outcome recording.
 */
class IntegrationAdminServiceTest {

    private static final String DARAJA = ProviderCatalog.DARAJA;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IntegrationConfigRepository repository;
    private IntegrationEncryptionService encryptionService;
    private IntegrationRegistry registry;
    private ProviderTesterRegistry testerRegistry;
    private IntegrationAuditLogJpaRepository auditLogRepository;
    private IntegrationAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationConfigRepository.class);
        encryptionService = mock(IntegrationEncryptionService.class);
        registry = mock(IntegrationRegistry.class);
        testerRegistry = mock(ProviderTesterRegistry.class);
        auditLogRepository = mock(IntegrationAuditLogJpaRepository.class);
        service = new IntegrationAdminService(
                repository, encryptionService, registry, testerRegistry, auditLogRepository, objectMapper);
        when(encryptionService.decryptPayload(anyString()))
                .thenAnswer(inv -> {
                    String value = inv.getArgument(0);
                    return value.startsWith("ENC:") ? value.substring(4) : value;
                });
        when(encryptionService.encryptPayload(anyString()))
                .thenAnswer(inv -> "ENC:" + inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static IntegrationConfig configWith(String providerKey, IntegrationEnvironment env,
                                                String encryptedJson) {
        IntegrationConfig config = IntegrationConfig.newUnconfigured(providerKey, env);
        config.replaceCredentials(encryptedJson, 1, "owner-1");
        return config;
    }

    private static IntegrationDtos.FieldView field(IntegrationDtos.EnvironmentView envView, String key) {
        return envView.fields().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Test payload did not parse", e);
        }
    }

    // ---------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------

    @Test
    void list_returnsAllProvidersUnconfigured() {
        when(repository.find(anyString(), any(IntegrationEnvironment.class))).thenReturn(Optional.empty());

        List<IntegrationDtos.ProviderView> views = service.list();

        assertThat(views).hasSize(6);
        IntegrationDtos.EnvironmentView dev =
                views.stream().filter(v -> v.providerKey().equals(DARAJA)).findFirst().orElseThrow()
                        .environments().get(0);
        assertThat(dev.environment()).isEqualTo("DEVELOPMENT");
        assertThat(dev.status()).isEqualTo(IntegrationStatus.NOT_CONFIGURED.name());
        assertThat(dev.configured()).isFalse();
        assertThat(dev.fields()).allMatch(f -> !f.configured() && f.value().isEmpty());
    }

    @Test
    void get_masksSecretsButShowsNonSecretValues() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT,
                "{\"consumer_key\":\"ck123\",\"consumer_secret\":\"cs-secret\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(config));

        IntegrationDtos.ProviderView view = service.get(DARAJA);
        IntegrationDtos.EnvironmentView dev =
                view.environments().stream().filter(e -> e.environment().equals("DEVELOPMENT")).findFirst().orElseThrow();

        IntegrationDtos.FieldView consumerSecret = field(dev, "consumer_secret");
        assertThat(consumerSecret.configured()).isTrue();
        assertThat(consumerSecret.value()).isEqualTo("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022cret");
        assertThat(consumerSecret.value()).doesNotContain("cs-secret");

        IntegrationDtos.FieldView consumerKey = field(dev, "consumer_key");
        assertThat(consumerKey.configured()).isTrue();
        assertThat(consumerKey.value()).isEqualTo("ck123");
    }

    @Test
    void audit_capsLimitAndParsesMetadata() {
        IntegrationAuditLogEntity entry = new IntegrationAuditLogEntity(
                DARAJA, "PRODUCTION", "updated", "owner-1",
                "{\"fields\":{\"consumer_secret\":\"<redacted:changed>\"}}", "10.0.0.1");
        when(auditLogRepository.findTop50ByProviderKeyOrderByCreatedAtDesc(DARAJA))
                .thenReturn(List.of(entry, entry, entry, entry, entry));

        List<IntegrationDtos.AuditEntryView> result = service.audit(DARAJA, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).actorUserId()).isEqualTo("owner-1");
        assertThat(result.get(0).action()).isEqualTo("updated");
        assertThat(result.get(0).metadata()).containsKey("fields");
    }

    @Test
    void audit_limitBelowOne_isCappedToOne() {
        when(auditLogRepository.findTop50ByProviderKeyOrderByCreatedAtDesc(DARAJA))
                .thenReturn(List.of(new IntegrationAuditLogEntity(
                        DARAJA, "PRODUCTION", "created", "owner-1", null, null)));

        assertThat(service.audit(DARAJA, 0)).hasSize(1);
    }

    @Test
    void unknownProvider_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.get("not-a-provider"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown integration provider");
    }

    // ---------------------------------------------------------------
    // Update (upsert)
    // ---------------------------------------------------------------

    @Test
    void update_newConfig_encryptsOnlyDefinitionFieldsAndAuditsCreation() {
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.empty());

        service.update(DARAJA, IntegrationEnvironment.DEVELOPMENT,
                Map.of("consumer_key", "ck", "consumer_secret", "cs", "unknown_field", "dropped"),
                "owner-1", "10.0.0.1");

        ArgumentCaptor<IntegrationConfig> savedCaptor = ArgumentCaptor.forClass(IntegrationConfig.class);
        verify(repository).save(savedCaptor.capture());
        IntegrationConfig saved = savedCaptor.getValue();
        assertThat(saved.getEncryptedCredentials()).startsWith("ENC:");
        Map<String, Object> stored = readMap(saved.getEncryptedCredentials().substring(4));
        assertThat(stored)
                .containsEntry("consumer_key", "ck")
                .containsEntry("consumer_secret", "cs")
                .doesNotContainKey("unknown_field");
        assertThat(stored).hasSize(10);
        assertThat(saved.getStatus()).isEqualTo(IntegrationStatus.CONFIGURED);
        assertThat(saved.getUpdatedBy()).isEqualTo("owner-1");

        verify(registry).invalidate(DARAJA);

        ArgumentCaptor<IntegrationAuditLogEntity> auditCaptor = ArgumentCaptor.forClass(IntegrationAuditLogEntity.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        IntegrationAuditLogEntity audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo("created");
        assertThat(audit.getActorUserId()).isEqualTo("owner-1");
        assertThat(audit.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(audit.getEnvironment()).isEqualTo(IntegrationEnvironment.DEVELOPMENT.name());
        assertThat(audit.getMetadata()).contains("\"initial\"");
    }

    @Test
    void update_keepsExistingSecretWhenRequestFieldBlank() {
        IntegrationConfig existing = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT,
                "{\"consumer_key\":\"old-ck\",\"consumer_secret\":\"old-secret\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(existing));

        service.update(DARAJA, IntegrationEnvironment.DEVELOPMENT,
                Map.of("consumer_key", "new-ck", "consumer_secret", ""),
                "owner-2", "10.0.0.2");

        ArgumentCaptor<IntegrationConfig> savedCaptor = ArgumentCaptor.forClass(IntegrationConfig.class);
        verify(repository).save(savedCaptor.capture());
        Map<String, Object> stored = readMap(savedCaptor.getValue().getEncryptedCredentials().substring(4));
        assertThat(stored)
                .containsEntry("consumer_key", "new-ck")
                .containsEntry("consumer_secret", "old-secret");

        ArgumentCaptor<IntegrationAuditLogEntity> auditCaptor = ArgumentCaptor.forClass(IntegrationAuditLogEntity.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        Map<String, Object> metadata = readMap(auditCaptor.getValue().getMetadata());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) metadata.get("fields");
        assertThat(fields.get("consumer_secret")).isEqualTo("<redacted:unchanged>");
        assertThat(fields.get("consumer_key")).isEqualTo("new-ck");
    }

    // ---------------------------------------------------------------
    // Activate
    // ---------------------------------------------------------------

    @Test
    void activate_development_deactivatesSiblingAndActivates() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT, "{\"consumer_key\":\"ck\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(config));

        IntegrationDtos.ActivateResultView result =
                service.activate(DARAJA, IntegrationEnvironment.DEVELOPMENT, "owner-1", "10.0.0.1");

        verify(repository).deactivateAll(DARAJA, IntegrationEnvironment.DEVELOPMENT);
        assertThat(result.environment().active()).isTrue();
        verify(registry).invalidate(DARAJA);
    }

    @Test
    void activate_production_withoutVerifiedStatus_isRefused() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.PRODUCTION, "{\"consumer_key\":\"ck\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.PRODUCTION)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.activate(DARAJA, IntegrationEnvironment.PRODUCTION, "owner-1", "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Test Connection");
    }

    @Test
    void activate_production_withVerifiedStatus_activates() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.PRODUCTION, "{\"consumer_key\":\"ck\"}");
        config.markVerified("owner-1");
        when(repository.find(DARAJA, IntegrationEnvironment.PRODUCTION)).thenReturn(Optional.of(config));

        IntegrationDtos.ActivateResultView result =
                service.activate(DARAJA, IntegrationEnvironment.PRODUCTION, "owner-1", "10.0.0.1");

        assertThat(result.environment().active()).isTrue();
        verify(repository).deactivateAll(DARAJA, IntegrationEnvironment.PRODUCTION);
    }

    @Test
    void activate_withNothingSaved_throws() {
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(DARAJA, IntegrationEnvironment.DEVELOPMENT, "owner-1", "ip"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("save credentials first");
    }

    // ---------------------------------------------------------------
    // Test connection
    // ---------------------------------------------------------------

    @Test
    void test_success_marksVerifiedAndAudits() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT, "{\"consumer_key\":\"ck\",\"consumer_secret\":\"cs\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(config));
        ProviderTester tester = mock(ProviderTester.class);
        when(tester.test(any(), isNull(), isNull()))
                .thenReturn(ProviderTester.TestResult.success("OAuth token acquired"));
        when(testerRegistry.get(DARAJA)).thenReturn(tester);
        when(registry.resolveSaved(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Map.of());

        IntegrationDtos.TestConnectionView result =
                service.test(DARAJA, IntegrationEnvironment.DEVELOPMENT, null, "owner-1", "10.0.0.1");

        assertThat(result.ok()).isTrue();
        assertThat(result.status()).isEqualTo(IntegrationStatus.VERIFIED.name());
        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.VERIFIED);
        assertThat(config.getLastVerifiedBy()).isEqualTo("owner-1");
        verify(registry).invalidate(DARAJA);
    }

    @Test
    void test_failure_marksErrorAndAuditsFailure() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT, "{\"consumer_key\":\"ck\",\"consumer_secret\":\"bad\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(config));
        ProviderTester tester = mock(ProviderTester.class);
        when(tester.test(any(), isNull(), isNull()))
                .thenReturn(ProviderTester.TestResult.failure(
                        "Daraja rejected the credentials (401)", "Bad credentials"));
        when(testerRegistry.get(DARAJA)).thenReturn(tester);

        IntegrationDtos.TestConnectionView result =
                service.test(DARAJA, IntegrationEnvironment.DEVELOPMENT, null, "owner-1", "ip");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("Bad credentials");
        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.ERROR);
        assertThat(config.getLastError()).isEqualTo("Bad credentials");
    }

    @Test
    void test_whenTesterCrashes_returnsFailureWithoutPropagating() {
        IntegrationConfig config = configWith(
                DARAJA, IntegrationEnvironment.DEVELOPMENT, "{\"consumer_key\":\"ck\",\"consumer_secret\":\"cs\"}");
        when(repository.find(DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.of(config));
        ProviderTester tester = mock(ProviderTester.class);
        when(tester.test(any(), isNull(), isNull()))
                .thenThrow(new RuntimeException("connection pool exhausted"));
        when(testerRegistry.get(DARAJA)).thenReturn(tester);

        IntegrationDtos.TestConnectionView result =
                service.test(DARAJA, IntegrationEnvironment.DEVELOPMENT, null, "owner-1", "ip");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Test connection crashed");
        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.ERROR);
    }
}
