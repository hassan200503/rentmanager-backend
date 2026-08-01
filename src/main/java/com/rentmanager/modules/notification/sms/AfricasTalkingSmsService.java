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

    @Override
    public void sendSignInLink(String phone, String linkUrl) {
        send(phone, buildSignInLinkMessage(linkUrl));
    }

    @Override
    public void sendRentPaymentReceivedConfirmation(String phone, String amount, String receiptNumber) {
        send(phone, buildRentPaymentReceivedMessage(amount, receiptNumber));
    }

    @Override
    public void sendRentPaymentNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber) {
        send(phone, buildLandlordNotificationMessage(tenantName, amount, unitNumber));
    }

    @Override
    public void sendRentUpcomingPaymentReminder(String phone, String amount, String dueDate) {
        send(phone, buildUpcomingPaymentReminderMessage(amount, dueDate));
    }

    @Override
    public void sendRentOverdueReminder(String phone, String amount, String daysOverdue) {
        send(phone, buildOverdueReminderMessage(amount, daysOverdue));
    }

    @Override
    public void sendRentOverdueNotificationToLandlord(String phone, String tenantName, String amount, String unitNumber, String daysOverdue) {
        send(phone, buildLandlordOverdueNotificationMessage(tenantName, amount, unitNumber, daysOverdue));
    }

    @Override
    public void sendMaintenanceRequestConfirmation(String phone, String title, String requestId) {
        send(phone, buildMaintenanceRequestConfirmationMessage(title, requestId));
    }

    @Override
    public void sendMaintenanceRequestNotificationToLandlord(String phone, String tenantName, String unitNumber, String title) {
        send(phone, buildMaintenanceRequestNotificationToLandlordMessage(tenantName, unitNumber, title));
    }

    @Override
    public void sendMaintenanceRequestStatusUpdate(String phone, String title, String status) {
        send(phone, buildMaintenanceStatusUpdateMessage(title, status));
    }

    @Override
    public void sendAutoPayConfirmation(String phone, String amount, String mpesaPhone) {
        send(phone, buildAutoPayConfirmationMessage(amount, mpesaPhone));
    }

    @Override
    public void sendAutoPayFailed(String phone, String reason) {
        send(phone, buildAutoPayFailedMessage(reason));
    }

    @Override
    public boolean sendRaw(String phone, String message) {
        return send(phone, message);
    }

    private boolean send(String phone, String message) {
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
            return success;

        } catch (Exception ex) {
            log.error("SMS send failed. phone={}, error={}", PhoneMasker.mask(normalized), ex.getMessage());
            return false;
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

    private String buildSignInLinkMessage(String linkUrl) {
        return """
                Your RentManager portal is ready.
                Click this link to sign in (expires in 7 days):
                %s
                - RentManager""".formatted(linkUrl);
    }

    private String buildRentPaymentReceivedMessage(String amount, String receiptNumber) {
        return """
                Payment of Ksh %s received.
                Receipt: %s
                - RentManager""".formatted(amount, receiptNumber);
    }

    private String buildLandlordNotificationMessage(String tenantName, String amount, String unitNumber) {
        return """
                Rent received: %s has paid Ksh %s for unit %s.
                - RentManager""".formatted(tenantName, amount, unitNumber);
    }

    private String buildUpcomingPaymentReminderMessage(String amount, String dueDate) {
        return """
                Reminder: Ksh %s rent is due on %s.
                To pay, log in to your RentManager portal.
                - RentManager""".formatted(amount, dueDate);
    }

    private String buildOverdueReminderMessage(String amount, String daysOverdue) {
        return """
                Reminder: Ksh %s rent is %s day(s) overdue.
                Please pay immediately to avoid penalties.
                Log in to your RentManager portal.
                - RentManager""".formatted(amount, daysOverdue);
    }

    private String buildLandlordOverdueNotificationMessage(String tenantName, String amount, String unitNumber, String daysOverdue) {
        return """
                Overdue: %s's rent of Ksh %s for unit %s is %s day(s) overdue.
                - RentManager""".formatted(tenantName, amount, unitNumber, daysOverdue);
    }

    private String buildMaintenanceRequestConfirmationMessage(String title, String requestId) {
        return """
                Maintenance request received: "%s".
                Ref: %s. We will notify you when there is an update.
                - RentManager""".formatted(title, requestId);
    }

    private String buildMaintenanceRequestNotificationToLandlordMessage(String tenantName, String unitNumber, String title) {
        return """
                Maintenance request from %s for unit %s: "%s".
                Log in to your dashboard to review and assign.
                - RentManager""".formatted(tenantName, unitNumber, title);
    }

    private String buildMaintenanceStatusUpdateMessage(String title, String status) {
        return """
                Update on "%s": %s.
                Log in to your RentManager portal for details.
                - RentManager""".formatted(title, status.toLowerCase().replace('_', ' '));
    }

    private String buildAutoPayConfirmationMessage(String amount, String mpesaPhone) {
        return """
                Auto-pay successful! Ksh %s has been charged to %s.
                View your receipt in the RentManager portal.
                - RentManager""".formatted(amount, mpesaPhone);
    }

    private String buildAutoPayFailedMessage(String reason) {
        return """
                Auto-pay failed: %s
                Please log in to your RentManager portal to resolve.
                - RentManager""".formatted(reason);
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