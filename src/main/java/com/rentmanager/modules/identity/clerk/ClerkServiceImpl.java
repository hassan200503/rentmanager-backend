package com.rentmanager.modules.identity.clerk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public String createTenantUser(String fullName, String email, String phone, String password) {

        String existingUserId = findUserIdByEmail(email);
        if (existingUserId != null) {
            log.info("Clerk user already exists for email={}, reusing id={}", email, existingUserId);
            return existingUserId;
        }

        String[] names = splitFullName(fullName);
        String firstName = names[0];
        String lastName = names[1];

        Map<String, Object> body = Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "email_address", List.of(email),
                "phone_number", List.of(normalizePhone(phone)),
                "password", password,
                "skip_password_checks", true
        );

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
            log.info("Clerk user created. clerkUserId={}", clerkUserId);
            return clerkUserId;

        } catch (HttpClientErrorException e) {
            String fallbackId = findUserIdByEmail(email);
            if (fallbackId != null) {
                log.warn("Clerk createTenantUser hit duplicate error, resolved existing id={}", fallbackId);
                return fallbackId;
            }
            log.error("Clerk user creation failed for email={}", email, e);
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
            // Best-effort: never let a compensation failure mask the original
            // error, or throw from inside a saga's failure-handling path.
            log.error("Failed to delete Clerk user during compensation. clerkUserId={} — " +
                    "MANUAL CLEANUP MAY BE REQUIRED", clerkUserId, e);
        }
    }

    private String findUserIdByEmail(String email) {
        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = properties.getBaseUrl() + "/users?email_address[]=" + email;

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
        );

        Map<?, ?> body = response.getBody();
        if (body == null || !body.containsKey("data")) {
            return null;
        }

        List<?> users = (List<?>) body.get("data");
        if (users == null || users.isEmpty()) {
            return null;
        }

        Map<?, ?> firstMatch = (Map<?, ?>) users.get(0);
        return (String) firstMatch.get("id");
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