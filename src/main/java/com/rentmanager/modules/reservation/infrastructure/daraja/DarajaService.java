package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
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
     * Initiates an STK Push to the customer's M-Pesa number, authenticated
     * against a specific landlord's own Daraja credentials rather than a
     * single shared platform-wide config. callbackUrl and baseUrl remain
     * global (sourced from DarajaProperties) since they describe routing
     * back to this platform's own single callback endpoint, not anything
     * landlord-specific.
     *
     * @param mpesaPhone  phone in +254XXXXXXXXX format
     * @param amount      deposit amount
     * @param accountRef  shown on customer's M-Pesa screen (e.g. unit number)
     * @param description short description shown on prompt
     * @param credentials the landlord's own Daraja credentials — caller is
     *                    responsible for having already verified
     *                    credentials.isConfigured() before calling this
     * @return CheckoutRequestID from Daraja — store this to match the callback
     */
    public String initiateSTKPush(
            String mpesaPhone,
            BigDecimal amount,
            String accountRef,
            String description,
            DarajaCredentials credentials
    ) {
        return initiateSTKPush(mpesaPhone, amount, accountRef, description, credentials, properties.getCallbackUrl());
    }

    /**
     * Same as the 5-arg {@code initiateSTKPush}, but takes an explicit
     * {@code callbackUrl} instead of always using the deposit flow's
     * {@code properties.getCallbackUrl()}.
     *
     * WHY THIS EXISTS: Daraja's CallBackURL is set per STK-push-request, not
     * per Daraja app registration — whatever URL is sent here is exactly
     * where Safaricom POSTs the result. The 5-arg overload previously
     * hardcoded the reservation module's own callback URL internally, which
     * was harmless while only the deposit flow called this method, but
     * would have silently broken rent payments the moment a second caller
     * (RentPaymentInitiationService) reused it: every rent-payment STK push
     * would have had its callback delivered to
     * ReservationController#mpesaCallback instead of the rent-payment
     * callback endpoint, where MpesaCallbackService would fail to find a
     * matching PaymentIntent and throw — meaning the customer's payment
     * would succeed on their phone but never post to the rent ledger.
     *
     * The 5-arg overload is kept and unchanged for the existing deposit
     * call site (InitiateReservationServiceImpl) — this is purely additive.
     */
    public String initiateSTKPush(
            String mpesaPhone,
            BigDecimal amount,
            String accountRef,
            String description,
            DarajaCredentials credentials,
            String callbackUrl
    ) {
        if (credentials == null || !credentials.isConfigured()) {
            // Defensive guard, not the primary check — callers (e.g.
            // UnitReservationTransactionService) should already validate
            // isConfigured() before ever reaching this point, so real
            // credentials never need to be resolved this deep in the call
            // chain unless something upstream skipped that check.
            throw new DarajaException(
                    "Cannot initiate STK Push: landlord has not configured Daraja credentials"
            );
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new DarajaException("Cannot initiate STK Push: callbackUrl is required");
        }

        String token = fetchAccessToken(credentials);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = generatePassword(timestamp, credentials);
        String phone = normalizePhone(mpesaPhone);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", credentials.getBusinessShortCode());
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amount.setScale(0, java.math.RoundingMode.CEILING).toBigInteger());
        body.put("PartyA", phone);
        body.put("PartyB", credentials.getBusinessShortCode());
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", callbackUrl);
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

    private String fetchAccessToken(DarajaCredentials credentials) {
        String rawCredentials = credentials.getConsumerKey() + ":" + credentials.getConsumerSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    properties.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                    HttpMethod.GET,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("Failed to fetch Daraja access token", ex);
            throw new DarajaException("Failed to fetch Daraja access token", ex);
        }

        Map<?, ?> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new DarajaException("Failed to fetch Daraja access token — no access_token in response");
        }

        return (String) body.get("access_token");
    }

    private String generatePassword(String timestamp, DarajaCredentials credentials) {
        String raw = credentials.getBusinessShortCode() + credentials.getPasskey() + timestamp;
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