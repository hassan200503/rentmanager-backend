package com.rentmanager.modules.integration.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntegrationConfig aggregate invariants: an unconfigured record never
 * carries a null payload (the V68 schema persists a NOT NULL column), and
 * every status transition is explicit.
 */
class IntegrationConfigTest {

    @Test
    void newUnconfigured_hasNoCredentialsAndBlankPayload() {
        IntegrationConfig config = IntegrationConfig.newUnconfigured("daraja", IntegrationEnvironment.DEVELOPMENT);

        assertThat(config.hasCredentials()).isFalse();
        assertThat(config.getEncryptedCredentials()).isEqualTo("");
        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.NOT_CONFIGURED);
        assertThat(config.isActive()).isFalse();
        assertThat(config.getKeyVersion()).isEqualTo(1);
    }

    @Test
    void replaceCredentials_invalidatesPreviousVerification() {
        IntegrationConfig config = IntegrationConfig.newUnconfigured("daraja", IntegrationEnvironment.DEVELOPMENT);
        config.markVerified("owner-1");

        config.replaceCredentials("encrypted", 2, "owner-1");

        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.CONFIGURED);
        assertThat(config.getLastError()).isNull();
        assertThat(config.getKeyVersion()).isEqualTo(2);
        assertThat(config.getUpdatedBy()).isEqualTo("owner-1");
    }

    @Test
    void markError_truncatesReasonToTwoThousandCharacters() {
        IntegrationConfig config = IntegrationConfig.newUnconfigured("daraja", IntegrationEnvironment.DEVELOPMENT);

        config.markError("x".repeat(5000));

        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.ERROR);
        assertThat(config.getLastError()).hasSize(2000);
    }

    @Test
    void markVerified_setsVerificationMetadata() {
        IntegrationConfig config = IntegrationConfig.newUnconfigured("daraja", IntegrationEnvironment.DEVELOPMENT);

        config.markVerified("owner-1");

        assertThat(config.getStatus()).isEqualTo(IntegrationStatus.VERIFIED);
        assertThat(config.getLastVerifiedBy()).isEqualTo("owner-1");
        assertThat(config.getLastVerifiedAt()).isNotNull();
    }
}