package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

/**
 * STK status query (stkpushquery) mapping - terminal vs pending outcomes.
 * Manual mock() construction per AGENTS.md.
 */
class DarajaServiceStkQueryTest {

    private RestTemplate restTemplate;
    private DarajaProperties properties;
    private PlatformDarajaCredentialsResolver darajaResolver;
    private DarajaService service;

    private static final String CHECKOUT_ID = "ws_CO_test_1";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        properties = new DarajaProperties();
        properties.setBaseUrl("https://sandbox.safaricom.co.ke");
        properties.setConsumerKey("consumer-key");
        properties.setConsumerSecret("consumer-secret");
        properties.setBusinessShortCode("174379");
        properties.setPasskey("passkey");
        darajaResolver = mock(PlatformDarajaCredentialsResolver.class);

        service = new DarajaService(properties, restTemplate, darajaResolver);

        when(restTemplate.exchange(
                contains("/oauth/v1/generate"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("access_token", "TOKEN"), HttpStatus.OK));
    }

    private void stubQueryResponse(Map<String, Object> responseBody) {
        when(restTemplate.exchange(
                contains("/mpesa/stkpushquery/v1/query"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    private DarajaCredentials credentials() {
        return DarajaCredentials.of(
                "consumer-key", "consumer-secret", "174379", "passkey");
    }

    @Test
    void querySTKStatus_successfulPayment_mapsPaidWithReceipt() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ResultCode", "0");
        body.put("ResultDesc", "The service request is processed successfully.");
        body.put("MpesaReceiptNumber", "SFK0000001");
        stubQueryResponse(body);

        DarajaService.StkQueryResult result = service.querySTKStatus(CHECKOUT_ID, credentials());

        assertTrue(result.isSuccess());
        assertFalse(result.isPending());
        assertEquals("SFK0000001", result.mpesaReceiptNumber());
    }

    @Test
    void querySTKStatus_cancelledByUser_mapsTerminalFailure() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ResultCode", "1032");
        body.put("ResultDesc", "The transaction was cancelled by the user");
        stubQueryResponse(body);

        DarajaService.StkQueryResult result = service.querySTKStatus(CHECKOUT_ID, credentials());

        assertFalse(result.isSuccess());
        assertTrue(result.isTerminal());
        assertEquals("The transaction was cancelled by the user", result.resultDesc());
    }

    @Test
    void querySTKStatus_stillProcessing_mapsPending() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ResultCode", "1037");
        body.put("ResultDesc", "Request cancelled by user");
        stubQueryResponse(body);

        DarajaService.StkQueryResult result = service.querySTKStatus(CHECKOUT_ID, credentials());

        assertFalse(result.isSuccess());
        assertTrue(result.isPending());
    }

    @Test
    void querySTKStatus_missingResultCode_mapsPending() {
        stubQueryResponse(Map.of("ResponseCode", "0"));

        DarajaService.StkQueryResult result = service.querySTKStatus(CHECKOUT_ID, credentials());

        assertFalse(result.isSuccess());
        assertTrue(result.isPending());
    }

    @Test
    void querySTKStatus_postsCheckoutRequestIdAndBusinessShortCode() {
        stubQueryResponse(Map.of("ResultCode", "1037", "ResultDesc", "Processing"));

        service.querySTKStatus(CHECKOUT_ID, credentials());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                contains("/mpesa/stkpushquery/v1/query"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class));
        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals(CHECKOUT_ID, body.get("CheckoutRequestID"));
        assertEquals("174379", body.get("BusinessShortCode"));
        assertNotNull(body.get("Password"));
        assertNotNull(body.get("Timestamp"));
    }

    @Test
    void querySTKStatus_networkError_throwsDarajaException() {
        when(restTemplate.exchange(
                contains("/mpesa/stkpushquery/v1/query"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        HttpStatus.BAD_GATEWAY, "Bad gateway"));

        assertThrows(DarajaException.class,
                () -> service.querySTKStatus(CHECKOUT_ID, credentials()));
    }

    @Test
    void querySTKStatus_blankCheckoutRequestId_throws() {
        assertThrows(DarajaException.class,
                () -> service.querySTKStatus(" ", credentials()));
    }

    @Test
    void querySTKStatus_accessTokenCachedBetweenCalls() {
        stubQueryResponse(Map.of("ResultCode", "1037", "ResultDesc", "Processing"));

        service.querySTKStatus(CHECKOUT_ID, credentials());
        service.querySTKStatus(CHECKOUT_ID, credentials());

        verify(restTemplate, times(1)).exchange(
                contains("/oauth/v1/generate"),
                eq(HttpMethod.GET),
                any(),
                eq(Map.class));
        verify(restTemplate, times(2)).exchange(
                contains("/mpesa/stkpushquery/v1/query"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class));
    }

    @Test
    void querySTKStatus_rateLimitExhausted_skipsWithoutCallingDaraja() {
        stubQueryResponse(Map.of("ResultCode", "1037", "ResultDesc", "Processing"));

        service.querySTKStatus(CHECKOUT_ID, credentials());
        service.querySTKStatus(CHECKOUT_ID, credentials());
        service.querySTKStatus(CHECKOUT_ID, credentials());

        DarajaException ex = assertThrows(DarajaException.class,
                () -> service.querySTKStatus(CHECKOUT_ID, credentials()));

        assertEquals(
                "Daraja rate limit reached — skipping status query",
                ex.getMessage());
        verify(restTemplate, times(3)).exchange(
                contains("/mpesa/stkpushquery/v1/query"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class));
    }
}
