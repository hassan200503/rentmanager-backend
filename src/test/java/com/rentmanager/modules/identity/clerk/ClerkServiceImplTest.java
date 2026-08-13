package com.rentmanager.modules.identity.clerk;

import com.rentmanager.modules.integration.application.IntegrationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the backend-authoritative userType write primitive
 * (ClerkService.setPublicMetadata):
 *   - PATCHes /users/{id}/metadata with {"public_metadata": {...}} (the
 *     dedicated metadata endpoint — public_metadata on PATCH /users/{id}
 *     itself is deprecated by Clerk and returns 422)
 *   - sends the bearer secret from ClerkProperties
 *   - NEVER throws into a business transaction on Clerk API failure
 *     (callers rely on best-effort semantics — a metadata sync hiccup must
 *     not roll back a tenant provisioning/invite/fulfillment).
 */
class ClerkServiceImplTest {

    private static final String BASE_URL = "https://api.clerk.com/v1";
    private static final String SECRET = "sk_test_abc";
    private static final String USER_ID = "user_123";

    private RestTemplate restTemplate;
    private ClerkServiceImpl service;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor =
            ArgumentCaptor.forClass(HttpEntity.class);

    @BeforeEach
    void setUp() {
        ClerkProperties properties = new ClerkProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setSecretKey(SECRET);
        restTemplate = mock(RestTemplate.class);
        IntegrationRegistry integrationRegistry = mock(IntegrationRegistry.class);
        service = new ClerkServiceImpl(properties, restTemplate, integrationRegistry);
    }

    @Test
    void setPublicMetadata_patchesUsersEndpointWithMetadataEnvelope() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/users/" + USER_ID + "/metadata"),
                eq(HttpMethod.PATCH),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("id", USER_ID)));

        service.setPublicMetadata(USER_ID, Map.of(ClerkService.USER_TYPE_KEY, "landlord"));

        verify(restTemplate).exchange(
                eq(BASE_URL + "/users/" + USER_ID + "/metadata"),
                eq(HttpMethod.PATCH),
                requestCaptor.capture(),
                eq(Map.class)
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertThat(body).containsKey("public_metadata");
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) body.get("public_metadata");
        assertThat(metadata.get(ClerkService.USER_TYPE_KEY)).isEqualTo("landlord");

        String authHeader = requestCaptor.getValue().getHeaders().getOrDefault("Authorization", java.util.List.of())
                .stream().findFirst().orElse(null);
        assertThat(authHeader).isEqualTo("Bearer " + SECRET);
    }

    @Test
    void setPublicMetadata_clerkFailure_doesNotThrow() {
        when(restTemplate.exchange(any(String.class), any(), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new RestClientException("Clerk is down"));

        assertThatCode(() ->
                service.setPublicMetadata(USER_ID, Map.of(ClerkService.USER_TYPE_KEY, "renter"))
        ).doesNotThrowAnyException();
    }

    @Test
    void setPublicMetadata_usesCanonicalUserTypeKey() {
        // The canonical key and the metadata map must agree — the frontend
        // reads exactly this JSON key, so any drift here would silently
        // break persona classification.
        assertThat(ClerkService.USER_TYPE_KEY).isEqualTo("userType");
        assertThat(Objects.requireNonNull(ClerkService.USER_TYPE_KEY)).isNotBlank();
    }
}