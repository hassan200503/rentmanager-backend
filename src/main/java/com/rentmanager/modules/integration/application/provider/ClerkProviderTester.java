package com.rentmanager.modules.integration.application.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Real Clerk test: an authenticated, low-cost Backend SDK call (user list,
 * limit 1) proving the Secret Key is valid for the target instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClerkProviderTester implements ProviderTester {

    private static final String DEFAULT_BASE_URL = "https://api.clerk.com/v1";

    private final RestTemplate restTemplate;

    @Override
    public String providerKey() {
        return "clerk";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String secretKey = credentials.getOrDefault("secret_key", "");
        String baseUrl = firstNonBlank(baseUrlOverride, credentials.get("base_url"), DEFAULT_BASE_URL);

        if (secretKey.isBlank()) {
            return TestResult.failure("Missing credentials", "Secret Key is required before testing.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    baseUrl + "/users?limit=1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> users = response.getBody();
            int total = users == null ? 0 : users.size();
            return TestResult.success("Connected to Clerk instance at " + baseUrl +
                    " — Secret Key is valid" + (total == 0 ? " (no users returned)." : ". One or more users reachable."));
        } catch (HttpClientErrorException e) {
            log.warn("Clerk test connection failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return TestResult.failure("Clerk rejected the Secret Key (" + e.getStatusCode() + ")",
                    String.valueOf(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return TestResult.failure("Could not reach Clerk", String.valueOf(e.getMessage()));
        }
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