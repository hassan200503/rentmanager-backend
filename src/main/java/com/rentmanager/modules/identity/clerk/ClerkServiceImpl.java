package com.rentmanager.modules.identity.clerk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClerkServiceImpl implements ClerkService {

    private final ClerkProperties properties;
    private final RestTemplate restTemplate;

    @Override
    public ClerkUserCreationResult createTenantUser(String fullName, String email, String phone) {
        return createClerkUser(fullName, email, phone, null, "createTenantUser");
    }

    @Override
    public ClerkUserCreationResult createStaffUser(String fullName, String email, String phone, String password) {
        return createClerkUser(fullName, email, phone, password, "createStaffUser");
    }

    @Override
    public SignInTokenResult createSignInToken(String clerkUserId, int expiresInSeconds) {
        HttpHeaders headers = buildAuthHeaders();

        Map<String, Object> body = Map.of(
                "user_id", clerkUserId,
                "expires_in_seconds", expiresInSeconds
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/sign_in_tokens",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("id")) {
                throw new ClerkException("Sign-in token creation failed — no id in response");
            }

            String tokenId = (String) responseBody.get("id");
            String token = (String) responseBody.get("token");
            String url = (String) responseBody.get("url");

            log.info("Sign-in token created. clerkUserId={}, tokenId={}, expiresIn={}s",
                    clerkUserId, tokenId, expiresInSeconds);

            return new SignInTokenResult(tokenId, token, url);

        } catch (Exception e) {
            log.error("Sign-in token creation failed for clerkUserId={}", clerkUserId, e);
            throw new ClerkException("Sign-in token creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Shared Clerk API call underlying both createTenantUser and
     * createStaffUser. logContext is included purely for log
     * disambiguation between the two call sites — it has no effect on
     * behavior. Password may be null (tenant flow — no password set).
     */
    private ClerkUserCreationResult createClerkUser(
            String fullName,
            String email,
            String phone,
            String password,
            String logContext
    ) {

        String existingUserId = findUserIdByEmail(email);
        if (existingUserId != null) {
            log.info("[{}] Clerk user already exists for email={}, reusing id={}",
                    logContext, email, existingUserId);
            return new ClerkUserCreationResult(existingUserId, false);
        }

        String[] names = splitFullName(fullName);
        String firstName = names[0];
        String lastName = names[1];

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("first_name", firstName);
        body.put("last_name", lastName);
        body.put("email_address", List.of(email));
        body.put("phone_number", List.of(normalizePhone(phone)));
        if (password != null) {
            body.put("password", password);
            body.put("skip_password_checks", true);
        }

        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/users",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("id")) {
                throw new ClerkException("Clerk user creation failed — no id in response");
            }

            String clerkUserId = (String) responseBody.get("id");
            log.info("[{}] Clerk user created. clerkUserId={}", logContext, clerkUserId);
            return new ClerkUserCreationResult(clerkUserId, true);

        } catch (HttpClientErrorException e) {
            String fallbackId = findUserIdByEmail(email);
            if (fallbackId != null) {
                log.warn("[{}] hit duplicate error, resolved existing id={}", logContext, fallbackId);
                return new ClerkUserCreationResult(fallbackId, false);
            }
            log.error("[{}] Clerk user creation failed for email={}", logContext, email, e);
            throw new ClerkException("Clerk user creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return findUserIdByEmail(email) != null;
    }

    @Override
    public void deleteUser(String clerkUserId) {
        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    properties.getBaseUrl() + "/users/" + clerkUserId,
                    HttpMethod.DELETE,
                    request,
                    Void.class
            );
            log.info("Clerk user deleted as compensation. clerkUserId={}", clerkUserId);
        } catch (Exception e) {
            log.error("Failed to delete Clerk user during compensation. clerkUserId={} — " +
                    "MANUAL CLEANUP MAY BE REQUIRED", clerkUserId, e);
        }
    }

    private String findUserIdByEmail(String email) {
        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = properties.getBaseUrl() + "/users?email_address[]=" + email;

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<Map<String, Object>> users = response.getBody();
        if (users == null || users.isEmpty()) {
            return null;
        }

        return (String) users.get(0).get("id");
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String[] splitFullName(String fullName) {
        String trimmed = fullName.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0) {
            return new String[] { trimmed, "" };
        }
        return new String[] {
                trimmed.substring(0, spaceIndex),
                trimmed.substring(spaceIndex + 1).trim()
        };
    }

    private String normalizePhone(String phone) {
        if (!phone.startsWith("+")) {
            return "+" + phone;
        }
        return phone;
    }
}