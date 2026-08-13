package com.rentmanager.modules.integration.application.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Real Daraja test: fetches an OAuth token from the environment's actual
 * base URL. Proves Consumer Key/Secret are valid for that environment —
 * the same token the whole STK/B2C stack uses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DarajaProviderTester implements ProviderTester {

    private static final String DEFAULT_SANDBOX = "https://sandbox.safaricom.co.ke";

    private final RestTemplate restTemplate;

    @Override
    public String providerKey() {
        return "daraja";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String consumerKey = credentials.getOrDefault("consumer_key", "");
        String consumerSecret = credentials.getOrDefault("consumer_secret", "");
        String baseUrl = firstNonBlank(baseUrlOverride, credentials.get("base_url"), DEFAULT_SANDBOX);

        if (consumerKey.isBlank() || consumerSecret.isBlank()) {
            return TestResult.failure("Missing credentials",
                    "Consumer Key and Consumer Secret are required before testing.");
        }

        HttpHeaders headers = new HttpHeaders();
        String token = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        headers.setBasicAuth(consumerKey, consumerSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/oauth/v1/generate?grant_type=client_credentials",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            if (response.getBody() != null && response.getBody().containsKey("access_token")) {
                return TestResult.success(
                        "OAuth token acquired from " + baseUrl + " — Consumer Key/Secret are valid for this environment.");
            }
            return TestResult.failure("Provider did not return an access token",
                    "Unexpected response from " + baseUrl + ": " + response.getBody());
        } catch (HttpClientErrorException e) {
            String detail = extractError(e);
            log.warn("Daraja test connection failed: {} {}", e.getStatusCode(), detail);
            return TestResult.failure(
                    "Daraja rejected the credentials (" + e.getStatusCode() + ")",
                    detail);
        } catch (Exception e) {
            log.warn("Daraja test connection failed", e);
            return TestResult.failure("Could not reach Daraja", String.valueOf(e.getMessage()));
        }
    }

    private String extractError(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            Object code = body.get("errorCode");
            Object message = body.get("errorMessage");
            if (code != null || message != null) {
                return (code == null ? "" : code + " ") + (message == null ? "" : message);
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        String raw = e.getResponseBodyAsString();
        return raw == null || raw.isBlank() ? "HTTP " + e.getStatusCode().value() : raw;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}