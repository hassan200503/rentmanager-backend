package com.rentmanager.modules.notification.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "africastalking", name = "enabled", havingValue = "true")
public class AfricasTalkingSmsService implements SmsService {

    private final WebClient webClient;
    private final AfricasTalkingProperties props;

    public AfricasTalkingSmsService(AfricasTalkingProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("apiKey", props.getApiKey())
                .build();
    }

    @Override
    public void sendCredentials(String phone, String password) {
        send(phone, buildCredentialsMessage(password));
    }

    @Override
    public void sendReservationConfirmed(String phone) {
        send(phone, buildReservationConfirmedMessage());
    }

    private void send(String phone, String message) {
        String normalized = normalizePhoneNumber(phone);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", props.getUsername());
        form.add("to", normalized);
        form.add("message", message);
        if (props.getSenderId() != null && !props.getSenderId().isBlank()) {
            form.add("from", props.getSenderId());
        }

        try {
            AfricasTalkingResponse response = webClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(AfricasTalkingResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            boolean success = response != null
                    && response.smsMessageData() != null
                    && response.smsMessageData().recipients() != null
                    && response.smsMessageData().recipients().stream()
                    .anyMatch(r -> "Success".equalsIgnoreCase(r.status()));

            if (success) {
                log.info("SMS sent. phone={}", mask(normalized));
            } else {
                log.warn("SMS provider did not confirm delivery. phone={}, response={}",
                        mask(normalized), response);
            }

        } catch (Exception ex) {
            // Intentionally swallowed: SMS delivery must never fail reservation fulfillment.
            log.error("SMS send failed. phone={}, error={}", mask(normalized), ex.getMessage());
        }
    }

    private String buildCredentialsMessage(String password) {
        return """
                Deposit received! Your unit is reserved.
                Temp password: %s
                Please change your password after signing in.
                - RentManager""".formatted(password);
    }

    private String buildReservationConfirmedMessage() {
        return """
                Deposit received! Your unit is reserved.
                Sign in with your existing RentManager account.
                Forgot password? Use "Forgot password" on the sign-in page.
                - RentManager""";
    }

    private String normalizePhoneNumber(String raw) {
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

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, phone.length() - 4).replaceAll(".", "*") + phone.substring(phone.length() - 4);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AfricasTalkingResponse(SmsMessageData smsMessageData) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record SmsMessageData(String message, List<Recipient> recipients) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Recipient(String number, String status, String messageId, Double cost) {}
    }
}