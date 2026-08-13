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

import java.util.List;
import java.util.Map;

/**
 * Real Meta WhatsApp Cloud API test: verifies the permanent token against
 * the phone number (proves token + WABA + number validity), then — when the
 * owner supplies a recipient — sends one real template message and reports
 * Meta's own response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppProviderTester implements ProviderTester {

    private static final String GRAPH_BASE = "https://graph.facebook.com/v19.0";

    private final RestTemplate restTemplate;

    @Override
    public String providerKey() {
        return "whatsapp";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String accessToken = credentials.getOrDefault("access_token", "");
        String phoneNumberId = credentials.getOrDefault("phone_number_id", "");
        String templateName = credentials.getOrDefault("template_name", "");

        if (accessToken.isBlank() || phoneNumberId.isBlank()) {
            return TestResult.failure("Missing credentials",
                    "Permanent Access Token and Phone Number ID are required before testing.");
        }

        // 1. Phone number validity — cheap, real, proves the token works.
        String numberUrl = GRAPH_BASE + "/" + phoneNumberId
                + "?fields=display_phone_number,verified_name&access_token=" + accessToken;
        try {
            ResponseEntity<Map> numberResponse = restTemplate.exchange(
                    numberUrl, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<?, ?> numberBody = numberResponse.getBody();
            if (numberBody == null || numberBody.containsKey("error")) {
                return TestResult.failure("Meta rejected the token",
                        String.valueOf(numberBody == null ? "no response" : numberBody.get("error")));
            }
            Object rawNumber = numberBody.get("display_phone_number");
            String displayNumber = String.valueOf(rawNumber != null ? rawNumber : "?");
            log.info("WhatsApp test connection: phone number {} verified against the Graph API",
                    displayNumber);
        } catch (HttpClientErrorException e) {
            return TestResult.failure("Meta rejected the token (" + e.getStatusCode() + ")",
                    String.valueOf(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return TestResult.failure("Could not reach the Meta Graph API", String.valueOf(e.getMessage()));
        }

        // 2. Optional real template send to the owner-supplied recipient.
        if (target == null || target.isBlank()) {
            return TestResult.success(
                    "Token is valid — phone number reachable on the Graph API. Add a recipient to send a live template message.");
        }
        return sendTemplateMessage(accessToken, phoneNumberId, templateName, target);
    }

    private TestResult sendTemplateMessage(String accessToken, String phoneNumberId, String templateName, String target) {
        String name = templateName.isBlank() ? "rentmanager_test_v1" : templateName;
        Map<String, Object> templateBody = Map.of(
                "messaging_product", "whatsapp",
                "to", target,
                "type", "template",
                "template", Map.of(
                        "name", name,
                        "language", Map.of("code", "en"),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", List.of(Map.of("type", "text", "text", "RentManager test connection"))))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GRAPH_BASE + "/" + phoneNumberId + "/messages",
                    HttpMethod.POST,
                    new HttpEntity<>(templateBody, headers),
                    Map.class
            );
            Map<?, ?> body = response.getBody();
            if (body != null && body.containsKey("messages")) {
                String messageId = String.valueOf(
                        ((List<?>) body.get("messages")).get(0) instanceof Map<?, ?> m ? m.get("id") : "?");
                return TestResult.success("Template message accepted by Meta — messageId " + messageId
                        + (templateName.isBlank()
                                ? " (template \"" + name + "\" was assumed — set a configured template for real sends)"
                                : ""));
            }
            return TestResult.failure("Meta did not accept the message", String.valueOf(body));
        } catch (HttpClientErrorException e) {
            return TestResult.failure("Meta rejected the template message (" + e.getStatusCode() + ")",
                    String.valueOf(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return TestResult.failure("Could not send the template message", String.valueOf(e.getMessage()));
        }
    }
}