package com.rentmanager.modules.integration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import com.rentmanager.modules.integration.domain.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Runtime credential resolution: active database config first, legacy
 * environment properties as migration fallback, sentinel placeholders
 * treated as unconfigured, and 60-second caching invalidated on saves.
 */
class IntegrationRegistryTest {

    private IntegrationConfigRepository repository;
    private IntegrationEncryptionService encryptionService;
    private Environment environment;
    private IntegrationRegistry registry;

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationConfigRepository.class);
        encryptionService = mock(IntegrationEncryptionService.class);
        environment = mock(Environment.class);
        registry = new IntegrationRegistry(repository, encryptionService, new ObjectMapper(), environment);
    }

    private static IntegrationConfig configWithCredentials(String providerKey, IntegrationEnvironment env) {
        IntegrationConfig config = IntegrationConfig.newUnconfigured(providerKey, env);
        config.replaceCredentials("encrypted-payload", 1, "owner-1");
        return config;
    }

    @Test
    void resolve_activeDatabaseConfig_decryptsAndReturnsCredentials() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.DARAJA, IntegrationEnvironment.DEVELOPMENT);
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload"))
                .thenReturn("{\"consumer_key\":\"ck\",\"base_url\":\"https://sandbox.safaricom.co.ke\"}");

        IntegrationRegistry.ResolvedConfig resolved = registry.resolve(ProviderCatalog.DARAJA);

        assertThat(resolved.providerKey()).isEqualTo(ProviderCatalog.DARAJA);
        assertThat(resolved.environment()).isEqualTo(IntegrationEnvironment.DEVELOPMENT);
        assertThat(resolved.fromDatabase()).isTrue();
        assertThat(resolved.credentials())
                .containsEntry("consumer_key", "ck")
                .containsEntry("base_url", "https://sandbox.safaricom.co.ke");
    }

    @Test
    void resolve_noDatabaseRow_fallsBackToEnvironmentProperties() {
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.empty());
        when(environment.getProperty("daraja.consumer-key", "")).thenReturn("env-consumer-key");
        when(environment.getProperty("daraja.consumer-secret", "")).thenReturn("env-secret");

        IntegrationRegistry.ResolvedConfig resolved = registry.resolve(ProviderCatalog.DARAJA);

        assertThat(resolved.fromDatabase()).isFalse();
        assertThat(resolved.credentials())
                .containsEntry("consumer_key", "env-consumer-key")
                .containsEntry("consumer_secret", "env-secret");
    }

    @Test
    void databaseConfig_winsOverEnvironmentFallback() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.DARAJA, IntegrationEnvironment.DEVELOPMENT);
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload"))
                .thenReturn("{\"consumer_key\":\"db-key\"}");
        when(environment.getProperty("daraja.consumer-key", "")).thenReturn("env-key");

        IntegrationRegistry.ResolvedConfig resolved = registry.resolve(ProviderCatalog.DARAJA);

        assertThat(resolved.credentials()).containsEntry("consumer_key", "db-key");
        assertThat(resolved.fromDatabase()).isTrue();
    }

    @Test
    void sentinelPlaceholder_isTreatedAsUnconfigured() {
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.empty());
        when(environment.getProperty("daraja.consumer-key", "")).thenReturn("YOUR_CONSUMER_KEY");

        assertThat(registry.resolveOrNull(ProviderCatalog.DARAJA)).isNull();
        assertThatThrownBy(() -> registry.resolve(ProviderCatalog.DARAJA))
                .isInstanceOf(IntegrationNotConfiguredException.class);
    }

    @Test
    void unknownProvider_resolvesToNull() {
        assertThat(registry.resolveOrNull("not-a-provider")).isNull();
    }

    @Test
    void resolve_resultsAreCachedWithinTtl() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.AFRICASTALKING, IntegrationEnvironment.DEVELOPMENT);
        when(repository.findActive(ProviderCatalog.AFRICASTALKING)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload")).thenReturn("{\"username\":\"sandbox\"}");

        registry.resolve(ProviderCatalog.AFRICASTALKING);
        registry.resolve(ProviderCatalog.AFRICASTALKING);

        verify(repository, times(1)).findActive(ProviderCatalog.AFRICASTALKING);
    }

    @Test
    void invalidate_forcesFreshResolution() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.AFRICASTALKING, IntegrationEnvironment.DEVELOPMENT);
        when(repository.findActive(ProviderCatalog.AFRICASTALKING)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload")).thenReturn("{\"username\":\"sandbox\"}");

        registry.resolve(ProviderCatalog.AFRICASTALKING);
        registry.invalidate(ProviderCatalog.AFRICASTALKING);
        registry.resolve(ProviderCatalog.AFRICASTALKING);

        verify(repository, times(2)).findActive(ProviderCatalog.AFRICASTALKING);
    }

    @Test
    void activeEnvironment_reflectsResolvedConfig() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.DARAJA, IntegrationEnvironment.PRODUCTION);
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload")).thenReturn("{\"consumer_key\":\"ck\"}");

        assertThat(registry.activeEnvironment(ProviderCatalog.DARAJA))
                .isEqualTo(IntegrationEnvironment.PRODUCTION);
    }

    @Test
    void isConfigured_falseWhenNothingResolvable() {
        when(repository.findActive(ProviderCatalog.DARAJA)).thenReturn(Optional.empty());

        assertThat(registry.isConfigured(ProviderCatalog.DARAJA)).isFalse();
    }

    @Test
    void resolveSaved_prefersSavedRowForGivenEnvironment() {
        IntegrationConfig config = configWithCredentials(ProviderCatalog.DARAJA, IntegrationEnvironment.PRODUCTION);
        when(repository.find(ProviderCatalog.DARAJA, IntegrationEnvironment.PRODUCTION)).thenReturn(Optional.of(config));
        when(encryptionService.decryptPayload("encrypted-payload"))
                .thenReturn("{\"consumer_key\":\"prod-ck\"}");

        Map<String, String> credentials = registry.resolveSaved(ProviderCatalog.DARAJA, IntegrationEnvironment.PRODUCTION);

        assertThat(credentials).containsEntry("consumer_key", "prod-ck");
    }

    @Test
    void resolveSaved_withoutSavedRow_fallsBackToEnvironment() {
        when(repository.find(ProviderCatalog.DARAJA, IntegrationEnvironment.DEVELOPMENT)).thenReturn(Optional.empty());
        when(environment.getProperty("daraja.consumer-key", "")).thenReturn("env-consumer-key");

        Map<String, String> credentials = registry.resolveSaved(ProviderCatalog.DARAJA, IntegrationEnvironment.DEVELOPMENT);

        assertThat(credentials).containsEntry("consumer_key", "env-consumer-key");
    }
}
