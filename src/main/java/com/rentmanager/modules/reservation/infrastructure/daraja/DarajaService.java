package com.rentmanager.modules.reservation.infrastructure.daraja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DarajaService {

    private final DarajaProperties properties;
    private final RestTemplate restTemplate;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // -------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------

    /**
     * Initiates an STK Push to the customer's M-Pesa number.
     *
     * @param mpesaPhone  phone in +254XXXXXXXXX format
     * @param amount      deposit amount
     * @param accountRef  shown on customer's M-Pesa screen (e.g. unit number)
     * @param description short description shown on prompt
     * @return CheckoutRequestID from Daraja — store this to match the callback
     */
    public String initiateSTKPush(
            String mpesaPhone,
            BigDecimal amount,
            String accountRef,
            String description
    ) {
        String token = fetchAccessToken();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = generatePassword(timestamp);
        String phone = normalizePhone(mpesaPhone);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", properties.getBusinessShortCode());
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amount.setScale(0, java.math.RoundingMode.CEILING).toBigInteger());
        body.put("PartyA", phone);
        body.put("PartyB", properties.getBusinessShortCode());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", properties.getCallbackUrl());
        body.put("AccountReference", accountRef);
        body.put("TransactionDesc", description);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    properties.getBaseUrl() + "/mpesa/stkpush/v1/processrequest",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("STK Push request to Daraja failed", ex);
            throw new DarajaException("STK Push request failed", ex);
        }

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("CheckoutRequestID")) {
            log.error("STK Push response missing CheckoutRequestID. Response={}", responseBody);
            throw new DarajaException("STK Push failed — no CheckoutRequestID in response");
        }

        String checkoutRequestId = (String) responseBody.get("CheckoutRequestID");
        log.info("STK Push initiated. CheckoutRequestID={}", checkoutRequestId);
        return checkoutRequestId;
    }

    // -------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------

    private String fetchAccessToken() {
        String credentials = properties.getConsumerKey() + ":" + properties.getConsumerSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                HttpMethod.GET,
                request,
                Map.class
        );

        Map<?, ?> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new DarajaException("Failed to fetch Daraja access token");
        }

        return (String) body.get("access_token");
    }

    private String generatePassword(String timestamp) {
        String raw = properties.getBusinessShortCode() + properties.getPasskey() + timestamp;
        return Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // Normalize +254712345678 → 254712345678 (Daraja format)
    private String normalizePhone(String phone) {
        if (phone.startsWith("+")) {
            return phone.substring(1);
        }
        return phone;
    }
}