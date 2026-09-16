package com.rentmanager.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DeploymentSafetyGuardTest {

    private static MockEnvironment safe() {
        return new MockEnvironment()
                .withProperty(DeploymentSafetyGuard.STRICT_PROPERTY, "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://postgres:5432/rentmanager")
                .withProperty("spring.datasource.password", "a-long-random-database-password")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "https://clerk.example.com/.well-known/jwks.json")
                .withProperty("clerk.secret-key", "sk_test_abc")
                .withProperty("cors.allowed-origins", "https://app.example.com")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("daraja.callback-secret", "");
    }

    @Test
    void aSafeConfigurationWithNoProviderCredentialsStarts() {
        assertThat(DeploymentSafetyGuard.problems(safe())).isEmpty();
        assertThatNoException().isThrownBy(() -> new DeploymentSafetyGuard(safe()).verify());
    }

    @Test
    void theDeveloperDefaultsAreAllRefused() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(DeploymentSafetyGuard.STRICT_PROPERTY, "true")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/rentmanager")
                .withProperty("spring.datasource.password", "rentmanager")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "http://clerk.local/jwks")
                .withProperty("clerk.secret-key", "YOUR_CLERK_SECRET_KEY")
                .withProperty("cors.allowed-origins", "http://localhost:3000")
                .withProperty("daraja.callback-secret", "short");
        env.setActiveProfiles("dev");

        assertThat(DeploymentSafetyGuard.problems(env)).hasSize(9);
        assertThatThrownBy(() -> new DeploymentSafetyGuard(env).verify())
                .isInstanceOf(IllegalStateException.class)
                // Names settings, never echoes values.
                .hasMessageNotContaining("YOUR_CLERK_SECRET_KEY")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void aWildcardOrHttpOriginAmongValidOnesIsRefused() {
        assertThat(DeploymentSafetyGuard.problems(safe().withProperty("cors.allowed-origins", "https://app.example.com, *")))
                .singleElement().asString().contains("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void checksDoNotRunUnlessStrictModeIsOn() {
        MockEnvironment unsafe = new MockEnvironment().withProperty("spring.datasource.password", "rentmanager");
        assertThatNoException().isThrownBy(() -> new DeploymentSafetyGuard(unsafe).verify());
    }
}
