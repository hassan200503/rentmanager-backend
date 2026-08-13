package com.rentmanager.modules.integration.application.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Real Africa's Talking test: sends one actual SMS to the phone number the
 * owner supplies in the test dialog. The provider's own per-message status
 * is reported verbatim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfricasTalkingProviderTester implements ProviderTester {

    private static final String DEFAULT_SANDBOX = "https://api.sandbox.africastalking.com/version1/messaging";
    private static final String DEFAULT_TEST_BODY = "RentManager: test message from the Integrations console.";

    private final RestTemplate restTemplate;

    @Override
    public String providerKey() {
        return "africastalking";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String username = credentials.getOrDefault("username", "");
        String apiKey = credentials.getOrDefault("api_key", "");
        String senderId = credentials.getOrDefault("sender_id", "");
        String baseUrl = firstNonBlank(baseUrlOverride, credentials.get("base_url"), DEFAULT_SANDBOX);

        if (username.isBlank() || apiKey.isBlank()) {
            return TestResult.failure("Missing credentials",
                    "Username and API Key are required before testing.");
        }
        if (target == null || target.isBlank()) {
            return TestResult.failure("Recipient required",
                    "Type a phone number to receive the test SMS.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", normalizePhone(target));
        form.add("message", DEFAULT_TEST_BODY);
        if (!senderId.isBlank()) {
            form.add("from", senderId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("apiKey", apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<AtResponse> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    AtResponse.class
            );
            AtResponse body = response.getBody();
            String status = body != null && body.smsMessageData() != null
                    && body.smsMessageData().recipients() != null
                    && !body.smsMessageData().recipients().isEmpty()
                    ? body.smsMessageData().recipients().get(0).status()
                    : null;

            if (body != null && body.smsMessageData() != null && "Success".equalsIgnoreCase(status)) {
                return TestResult.success(
                        "Test SMS accepted by Africa's Talking — messageId "
                                + body.smsMessageData().recipients().get(0).messageId());
            }
            String detail = body != null && body.smsMessageData() != null
                    ? String.valueOf(body.smsMessageData().message())
                    : "HTTP " + response.getStatusCode().value();
            return TestResult.failure("Africa's Talking did not confirm delivery", detail);
        } catch (HttpClientErrorException e) {
            log.warn("Africa's Talking test connection failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return TestResult.failure("Africa's Talking rejected the request (" + e.getStatusCode() + ")",
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Africa's Talking test connection failed", e);
            return TestResult.failure("Could not reach Africa's Talking", String.valueOf(e.getMessage()));
        }
    }

    private static String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            return "+254" + digits.substring(1);
        }
        if (digits.startsWith("254")) {
            return "+" + digits;
        }
        if (raw.startsWith("+")) {
            return raw;
        }
        return "+254" + digits;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AtResponse(
            @JsonProperty("SMSMessageData") SmsMessageData smsMessageData
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record SmsMessageData(
                @JsonProperty("Message") String message,
                @JsonProperty("Recipients") List<Recipient> recipients
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Recipient(String number, String status, String messageId, String cost) {}
    }
}