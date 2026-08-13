package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DarajaService {

    private final DarajaProperties properties;
    private final RestTemplate restTemplate;
    private final PlatformDarajaCredentialsResolver darajaResolver;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Daraja OAuth tokens live ~59 minutes. We cache per credential-set
     * (consumer key) and refresh 4 minutes before expiry, halving the
     * HTTP round-trips per API call (every API call previously cost 2
     * requests: token fetch + call). Safaricom spike-arrest throttles at
     * 30 messages/minute with a 3-message burst, so every request counts.
     */
    private static final Duration TOKEN_SKEW = Duration.ofMinutes(4);
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Global token-bucket mirroring Safaricom's spike-arrest profile
     * (maxBurstMessageCount=3, 30 messages/60s = one token per 2s).
     * Shared across ALL Daraja flows (STK push, status query, Ratiba) —
     * Safaricom throttles per API key, not per flow, so the limiter must
     * be global too. Tuning constants:
     * - burst 3: allows one burst (e.g. retry reconcile: 1 push + 1 query)
     * - refill 1 token/2s: 30 messages/minute steady state
     */
    private static final int RATE_LIMIT_MAX_BURST = 3;
    private static final Duration RATE_LIMIT_REFILL = Duration.ofSeconds(2);
    private static final long RATE_LIMIT_WAIT_MILLIS = 10_000;
    private final DarajaRateLimiter rateLimiter =
            new DarajaRateLimiter(RATE_LIMIT_MAX_BURST, RATE_LIMIT_REFILL);

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

        acquireRateSlotOrWait();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    activeDaraja().baseUrl() + "/mpesa/stkpush/v1/processrequest",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("STK Push request to Daraja failed", ex);
            throw new DarajaException("STK Push request failed" + extractDarajaErrorMessage(ex), ex);
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

    /**
     * Surfaces Safaricom's own errorMessage (e.g. "Invalid CallBackURL")
     * in the DarajaException so the frontend can show the actionable
     * reason instead of a generic failure. Falls back to an empty string
     * when the response isn't a Daraja HTTP error or has no message.
     */
    private static String extractDarajaErrorMessage(Exception ex) {
        if (ex instanceof org.springframework.web.client.HttpClientErrorException httpEx) {
            String body = httpEx.getResponseBodyAsString();
            if (body != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("\"errorMessage\"\\s*:\\s*\"([^\"]*)\"")
                        .matcher(body);
                if (matcher.find()) {
                    String message = matcher.group(1);
                    if (message != null && !message.isBlank()) {
                        return ": " + message;
                    }
                }
            }
        }
        return "";
    }

    // -------------------------------------------------------
    // M-PESA RATIBA (STANDING ORDERS)
    // -------------------------------------------------------

    /**
     * Initiates a M-Pesa Ratiba standing-order creation for the customer
     * (Daraja {@code createStandingOrderExternal}). Safaricom sends the
     * customer an NI push (PIN prompt) which is the consent + MSISDN
     * ownership check; the final result (order ACTIVE or FAILED) arrives
     * asynchronously on {@code callbackUrl} - {@code ratibaResponseRefId}
     * is what ties that callback back to the local order record.
     *
     * <p>Uses the platform's own Daraja credentials (like rent payments),
     * not a landlord's credentials - these are platform-level billing
     * collections into our Paybill.</p>
     *
     * @param phone           customer MSISDN in 2547XXXXXXXX format
     * @param amount          whole KES amount (Ratiba does not support decimals)
     * @param accountReference max 12 chars; appears on the customer's M-Pesa
     *                         statement and is the C2B BillRefNumber later
     * @param startDate       first execution date (yyyyMMdd)
     * @param endDate         last execution date (yyyyMMdd)
     * @return the Ratiba responseRefID used to match the creation callback
     */
    public String createStandingOrder(
            String phone,
            BigDecimal amount,
            String accountReference,
            LocalDate startDate,
            LocalDate endDate,
            String callbackUrl
    ) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new DarajaException("Cannot create standing order: callbackUrl is required");
        }
        if (amount == null || amount.stripTrailingZeros().scale() > 0) {
            throw new DarajaException("Cannot create standing order: amount must be a whole number");
        }
        if (accountReference == null || accountReference.isBlank() || accountReference.length() > 12) {
            throw new DarajaException("Cannot create standing order: account reference must be 1-12 chars");
        }

        DarajaCredentials credentials = DarajaCredentials.of(
                activeDaraja().consumerKey(),
                activeDaraja().consumerSecret(),
                activeDaraja().businessShortCode(),
                activeDaraja().passkey()
        );
        String token = fetchAccessToken(credentials);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("StandingOrderName", "RentManager Premium");
        body.put("StartDate", startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        body.put("EndDate", endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        body.put("BusinessShortCode", activeDaraja().businessShortCode());
        body.put("TransactionType", "Standing Order Customer Pay Bill");
        body.put("ReceiverPartyIdentifierType", "4");
        body.put("Amount", amount.toBigInteger());
        body.put("PartyA", normalizePhone(phone));
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", accountReference);
        body.put("TransactionDesc", "Premium plan");
        body.put("Frequency", "4");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        acquireRateSlotOrWait();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    properties.getRatibaCreateStandingOrderUrl(),
                    HttpMethod.POST,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("Ratiba createStandingOrderExternal request to Daraja failed", ex);
            throw new DarajaException("Ratiba standing order creation request failed"
                    + extractDarajaErrorMessage(ex), ex);
        }

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("ResponseHeader")) {
            log.error("Ratiba response missing ResponseHeader. Response={}", responseBody);
            throw new DarajaException("Ratiba standing order creation failed - no ResponseHeader in response");
        }

        Map<?, ?> responseHeader = (Map<?, ?>) responseBody.get("ResponseHeader");
        String responseRefId = (String) responseHeader.get("responseRefID");
        if (responseRefId == null || responseRefId.isBlank()) {
            log.error("Ratiba response missing responseRefID. Response={}", responseBody);
            throw new DarajaException("Ratiba standing order creation failed - no responseRefID in response");
        }

        log.info("Ratiba standing order creation initiated. accountReference={} amount={} responseRefID={}",
                accountReference, amount, responseRefId);
        return responseRefId;
    }

    // -------------------------------------------------------
    // STK STATUS QUERY (POLLING FALLBACK)
    // -------------------------------------------------------

    /**
     * Outcome of an {@code /mpesa/stkpushquery/v1/query} call. ResultCode
     * semantics: "0" = paid (MpesaReceiptNumber present), "1032" =
     * cancelled by user, "1031"/"1" = insufficient funds, "1037" = still
     * processing (not terminal). Anything other than "0" / "1037" is a
     * terminal failure.
     */
    public record StkQueryResult(
            String resultCode,
            String resultDesc,
            String mpesaReceiptNumber
    ) {
        public boolean isSuccess() {
            return "0".equals(resultCode);
        }

        public boolean isPending() {
            return resultCode == null || "1037".equals(resultCode);
        }

        public boolean isTerminal() {
            return !isPending();
        }
    }

    /**
     * Queries the status of a previously initiated STK push. Used as a
     * polling fallback so flows (e.g. subscription billing) resolve to a
     * terminal state even when Safaricom never delivers the callback.
     *
     * @param checkoutRequestId from {@link #initiateSTKPush}
     * @param credentials       the credentials the push was initiated with
     */
    public StkQueryResult querySTKStatus(String checkoutRequestId, DarajaCredentials credentials) {
        if (credentials == null || !credentials.isConfigured()) {
            throw new DarajaException("Cannot query STK status: Daraja credentials are not configured");
        }
        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            throw new DarajaException("Cannot query STK status: CheckoutRequestID is required");
        }

        String token = fetchAccessToken(credentials);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String password = generatePassword(timestamp, credentials);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", credentials.getBusinessShortCode());
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("CheckoutRequestID", checkoutRequestId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        acquireRateSlotOrSkip();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    activeDaraja().baseUrl() + "/mpesa/stkpushquery/v1/query",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
        } catch (Exception ex) {
            log.error("STK status query to Daraja failed", ex);
            throw new DarajaException("STK status query failed" + extractDarajaErrorMessage(ex), ex);
        }

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null) {
            log.error("STK status query returned an empty response. CheckoutRequestID={}", checkoutRequestId);
            throw new DarajaException("STK status query failed — empty response");
        }

        String resultCode = responseBody.get("ResultCode") != null
                ? String.valueOf(responseBody.get("ResultCode"))
                : null;
        String resultDesc = responseBody.get("ResultDesc") != null
                ? String.valueOf(responseBody.get("ResultDesc"))
                : null;
        String receipt = responseBody.get("MpesaReceiptNumber") != null
                ? String.valueOf(responseBody.get("MpesaReceiptNumber"))
                : null;

        log.info("STK status queried. CheckoutRequestID={} ResultCode={} ResultDesc={}",
                checkoutRequestId, resultCode, resultDesc);
        return new StkQueryResult(resultCode, resultDesc, receipt);
    }

    // -------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------

    /**
     * The platform's active Daraja credentials resolved through the
     * Integration Registry (database config, then legacy daraja.* env as
     * fallback). Falls back to the bound properties when the resolver is
     * unavailable (unit tests) so behavior is unchanged in that case.
     */
    private PlatformDarajaCredentialsResolver.PlatformDarajaCredentials activeDaraja() {
        PlatformDarajaCredentialsResolver.PlatformDarajaCredentials resolved = darajaResolver.credentials();
        if (resolved != null) {
            return resolved;
        }
        return new PlatformDarajaCredentialsResolver.PlatformDarajaCredentials(
                properties.getConsumerKey(),
                properties.getConsumerSecret(),
                properties.getBusinessShortCode(),
                properties.getPasskey(),
                properties.getBaseUrl(),
                "", "", "", "");
    }

    private String fetchAccessToken(DarajaCredentials credentials) {
        String cacheKey = credentials.getConsumerKey();
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.token();
        }

        String rawCredentials = credentials.getConsumerKey() + ":" + credentials.getConsumerSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(rawCredentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    activeDaraja().baseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
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

        String token = (String) body.get("access_token");
        long expiresInSeconds = body.get("expires_in") instanceof Number number
                ? number.longValue()
                : 3599;
        tokenCache.put(cacheKey, new CachedToken(
                token,
                Instant.now().plus(Duration.ofSeconds(expiresInSeconds)).minus(TOKEN_SKEW)
        ));
        return token;
    }

    /**
     * Blocking slot for user-facing flows (STK push, Ratiba): waits up to
     * {@link #RATE_LIMIT_WAIT_MILLIS} for the bucket to refill before
     * giving up, so a momentarily saturated bucket does not fail a
     * customer's payment outright.
     */
    private void acquireRateSlotOrWait() {
        if (!rateLimiter.acquire(RATE_LIMIT_WAIT_MILLIS)) {
            log.warn("Daraja rate limit reached — giving up after {}ms of waiting",
                    RATE_LIMIT_WAIT_MILLIS);
            throw new DarajaException(
                    "Daraja rate limit reached — please try again in a moment");
        }
    }

    /**
     * Non-blocking slot for polling (STK status query): when the bucket is
     * exhausted the query is skipped rather than queued, because a poll
     * that just returns PENDING again is harmless — but a poll queue
     * holding the bucket would starve real payments. The caller treats
     * the resulting DarajaException as "still pending, try later".
     */
    private void acquireRateSlotOrSkip() {
        if (!rateLimiter.tryAcquire()) {
            log.info("Daraja rate limit reached — skipping STK status query");
            throw new DarajaException(
                    "Daraja rate limit reached — skipping status query");
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    /**
     * Simple synchronized token bucket matching Safaricom's spike-arrest
     * profile (3-message burst, 1 message per 2s steady state). Singleton
     * per DarajaService instance (which is a singleton bean), so every
     * flow competes for the same bucket — exactly how Safaricom throttles.
     */
    private static final class DarajaRateLimiter {

        private final int maxBurst;
        private final long refillNanos;
        private final Object lock = new Object();
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        private DarajaRateLimiter(int maxBurst, Duration refillPerToken) {
            this.maxBurst = maxBurst;
            this.refillNanos = refillPerToken.toNanos();
            this.tokens = maxBurst;
        }

        private boolean tryAcquire() {
            synchronized (lock) {
                refill();
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return true;
                }
                return false;
            }
        }

        private boolean acquire(long timeoutMillis) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            synchronized (lock) {
                while (true) {
                    refill();
                    if (tokens >= 1.0) {
                        tokens -= 1.0;
                        return true;
                    }
                    long now = System.nanoTime();
                    if (now >= deadline) {
                        return false;
                    }
                    long remainingNanos = deadline - now;
                    long waitMillis = Math.min(200,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1);
                    try {
                        TimeUnit.MILLISECONDS.timedWait(lock, waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(maxBurst, tokens + (double) elapsed / refillNanos);
                lastRefillNanos = now;
            }
        }
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