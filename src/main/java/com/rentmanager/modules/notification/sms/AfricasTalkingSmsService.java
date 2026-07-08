package com.rentmanager.modules.notification.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
                log.info("SMS sent. phone={}", PhoneMasker.mask(normalized));
            } else {
                log.warn("SMS provider did not confirm delivery. phone={}, response={}",
                        PhoneMasker.mask(normalized), response);
            }

        } catch (Exception ex) {
            // Intentionally swallowed: SMS delivery must never fail reservation fulfillment.
            log.error("SMS send failed. phone={}, error={}", PhoneMasker.mask(normalized), ex.getMessage());
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

    // Africa's Talking's response casing is inconsistent between levels:
    // the outer envelope and Message/Recipients keys are PascalCase, while
    // fields inside each recipient (cost, messageId, number, status) are
    // camelCase. Confirmed against a real sandbox response on 2026-07-08:
    // {"SMSMessageData":{"Message":"...","Recipients":[{"cost":"KES 1.6000",
    // "messageId":"...","number":"...","status":"Success","statusCode":101}]}}
    //
    // Note: "cost" is a formatted currency STRING (e.g. "KES 1.6000"), not a
    // numeric type — confirmed by a real deserialization failure when this
    // field was typed as Double. If a numeric amount is ever needed, parse
    // this string explicitly (strip currency code, parse remainder) rather
    // than relying on Jackson to coerce it.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AfricasTalkingResponse(
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