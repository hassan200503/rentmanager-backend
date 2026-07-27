package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DarajaB2CService {

    private final DarajaProperties darajaProperties;
    private final DarajaB2CProperties b2cProperties;
    private final RestTemplate restTemplate;

    /**
     * Initiates a B2C payment via Daraja's /b2c/v3/paymentrequest endpoint.
     * Uses platform-level credentials from DarajaProperties for the OAuth
     * token and the B2C-specific InitiatorName/SecurityCredential for the
     * request body.
     *
     * @return OriginatorConversationID from Daraja — store this to match callbacks
     */
    public String initiateB2C(
            BigDecimal amount,
            String recipientPhone,
            String recipientName,
            String remarks,
            String commandId
    ) {
        String token = fetchAccessToken();
        String phone = normalizePhone(recipientPhone);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("InitiatorName", b2cProperties.getInitiatorName());
        body.put("SecurityCredential", b2cProperties.getSecurityCredential());
        body.put("CommandID", commandId);
        body.put("Amount", amount.setScale(0, java.math.RoundingMode.CEILING).toBigInteger().toString());
        body.put("PartyA", darajaProperties.getBusinessShortCode());
        body.put("PartyB", phone);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", b2cProperties.getQueueTimeOutUrl());
        body.put("ResultURL", b2cProperties.getResultUrl());
        body.put("Occasion", remarks);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    darajaProperties.getBaseUrl() + "/mpesa/b2c/v3/paymentrequest",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("B2C request to Daraja failed", ex);
            throw new DarajaException("B2C request failed", ex);
        }

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null) {
            throw new DarajaException("B2C response body is null");
        }

        // ResponseCode 0 means Safaricom accepted the request
        Object responseCode = responseBody.get("ResponseCode");
        if (responseCode != null && !"0".equals(responseCode.toString())) {
            String responseDesc = responseBody.get("ResponseDescription") != null
                    ? responseBody.get("ResponseDescription").toString()
                    : "Unknown error";
            log.error("B2C request rejected by Daraja. ResponseCode={} ResponseDescription={}",
                    responseCode, responseDesc);
            throw new DarajaException("B2C request rejected: " + responseDesc);
        }

        String originatorConversationId = (String) responseBody.get("OriginatorConversationID");
        if (originatorConversationId == null) {
            log.error("B2C response missing OriginatorConversationID. Response={}", responseBody);
            throw new DarajaException("B2C failed — no OriginatorConversationID in response");
        }

        log.info("B2C initiated. OriginatorConversationID={} Amount={} Recipient={}",
                originatorConversationId, amount, phone);
        return originatorConversationId;
    }

    private String fetchAccessToken() {
        String rawCredentials = darajaProperties.getConsumerKey() + ":" + darajaProperties.getConsumerSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    darajaProperties.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                    HttpMethod.GET,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("Failed to fetch Daraja access token for B2C", ex);
            throw new DarajaException("Failed to fetch Daraja access token for B2C", ex);
        }

        Map<?, ?> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new DarajaException("Failed to fetch Daraja access token — no access_token in response");
        }

        return (String) body.get("access_token");
    }

    private String normalizePhone(String phone) {
        if (phone.startsWith("+")) {
            return phone.substring(1);
        }
        return phone;
    }
}