package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.config.SubscriptionBillingProperties;
import com.rentmanager.modules.tenant.infrastructure.daraja.SubscriptionPaymentCallbackTransactionService;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * STK-status polling reconciliation in the payment-request status poll
 * (the path that lets the frontend show success/failure without waiting
 * on the Safaricom callback). Manual mock() construction per AGENTS.md.
 */
class SubscriptionBillingServiceTest {

    private TenantRepository tenantRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private SubscriptionPaymentRequestRepository paymentRequestRepository;
    private UnitRepository unitRepository;
    private DarajaService darajaService;
    private DarajaProperties darajaProperties;
    private PlatformDarajaCredentialsResolver darajaResolver;
    private RatibaStandingOrderService ratibaStandingOrderService;
    private SubscriptionPaymentCallbackTransactionService callbackTransactionService;
    private SubscriptionBillingProperties subscriptionBillingProperties;

    private SubscriptionBillingService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String CHECKOUT_ID = "ws_CO_test_1";
    private static final String PLAN_CODE = "GROWTH";

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        paymentRequestRepository = mock(SubscriptionPaymentRequestRepository.class);
        unitRepository = mock(UnitRepository.class);
        darajaService = mock(DarajaService.class);
        darajaProperties = new DarajaProperties();
        darajaProperties.setConsumerKey("consumer-key");
        darajaProperties.setConsumerSecret("consumer-secret");
        darajaProperties.setBusinessShortCode("174379");
        darajaProperties.setPasskey("passkey");
        darajaResolver = mock(PlatformDarajaCredentialsResolver.class);
        ratibaStandingOrderService = mock(RatibaStandingOrderService.class);
        callbackTransactionService = mock(SubscriptionPaymentCallbackTransactionService.class);
        subscriptionBillingProperties = new SubscriptionBillingProperties();

        service = new SubscriptionBillingService(
                tenantRepository,
                subscriptionPlanRepository,
                paymentRequestRepository,
                unitRepository,
                darajaService,
                darajaProperties,
                darajaResolver,
                ratibaStandingOrderService,
                callbackTransactionService,
                subscriptionBillingProperties
        );
    }

    // ----------------------------------------------------------------
    // Fixtures for switchToPremium
    // ----------------------------------------------------------------

    private SubscriptionPlan buildPlan() {
        SubscriptionPlan plan = SubscriptionPlan.reconstruct(
                PLAN_CODE, "Growth", "desc", BillingCycle.MONTHLY,
                null, 100, null, null, new BigDecimal("5500.00"), null, true, true);
        plan.setId(PLAN_ID);
        return plan;
    }

    private Tenant buildCommissionTenant() {
        Tenant tenant = Tenant.create(
                "T-001", "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private void stubSwitchBasics() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildCommissionTenant()));
        when(subscriptionPlanRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(buildPlan()));
        when(unitRepository.countByTenantId(TENANT_ID)).thenReturn(0L);
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private SubscriptionPaymentRequest buildPendingRequest(Instant createdAt) {
        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.rehydrate(
                REQUEST_ID, 1L, createdAt, TENANT_ID, PLAN_ID,
                new BigDecimal("5500.00"), "254714931575",
                SubscriptionPaymentPurpose.INITIAL_ACTIVATION,
                SubscriptionPaymentRequestStatus.PENDING,
                CHECKOUT_ID, null, null
        );
        return request;
    }

    private SubscriptionPaymentRequest buildPendingRequest() {
        SubscriptionPaymentRequest request = buildPendingRequest(Instant.now());
        when(paymentRequestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void pendingRequest_whenStkQueryReportsPaid_appliesSuccessfulCallback() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("0", "Success", "SFK0000001"));

        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        verify(callbackTransactionService).processSuccessfulCallback(CHECKOUT_ID, "SFK0000001");
        verify(callbackTransactionService, never()).processFailedCallback(any(), any());
    }

    @Test
    void pendingRequest_whenStkQueryReportsPaidWithoutReceipt_usesFallbackReceipt() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("0", "Success", null));

        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        verify(callbackTransactionService).processSuccessfulCallback(CHECKOUT_ID, "QRY-" + CHECKOUT_ID);
    }

    @Test
    void pendingRequest_whenStkQueryReportsTerminalFailure_appliesFailedCallback() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult(
                        "1032", "The transaction was cancelled by the user", null));

        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        verify(callbackTransactionService).processFailedCallback(
                CHECKOUT_ID, "The transaction was cancelled by the user");
        verify(callbackTransactionService, never()).processSuccessfulCallback(any(), any());
    }

    @Test
    void pendingRequest_whenStkQueryStillProcessing_staysPending() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("1037", "Request cancelled by user", null));

        SubscriptionPaymentRequest result = service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        assertEquals(SubscriptionPaymentRequestStatus.PENDING, result.getStatus());
        verify(callbackTransactionService, never()).processSuccessfulCallback(any(), any());
        verify(callbackTransactionService, never()).processFailedCallback(any(), any());
    }

    @Test
    void pendingRequest_whenStkQueryFails_staysPendingWithoutThrowing() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenThrow(new DarajaException("STK status query failed"));

        assertDoesNotThrow(() -> service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID));

        verify(callbackTransactionService, never()).processSuccessfulCallback(any(), any());
        verify(callbackTransactionService, never()).processFailedCallback(any(), any());
    }

    @Test
    void paidRequest_doesNotQueryDaraja() {
        SubscriptionPaymentRequest request = buildPendingRequest();
        request.markPaid("SFK0000001");
        when(paymentRequestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));

        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        verify(darajaService, never()).querySTKStatus(any(), any());
        verify(callbackTransactionService, never()).processSuccessfulCallback(any(), any());
    }

    @Test
    void pendingRequest_secondPollWithinThrottleWindow_doesNotRequeryDaraja() {
        buildPendingRequest();
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("1037", "Processing", null));

        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);
        service.getPaymentRequestStatus(TENANT_ID, REQUEST_ID);

        verify(darajaService, times(1)).querySTKStatus(eq(CHECKOUT_ID), any());
    }

    // ----------------------------------------------------------------
    // switchToPremium retry reconciliation (stale-pending unblocking)
    // ----------------------------------------------------------------

    @Test
    void switchRetry_whenPendingRequestResolvesPaidViaQuery_returnsPaidRequestWithoutNewPush() {
        stubSwitchBasics();
        SubscriptionPaymentRequest pending = buildPendingRequest();
        when(paymentRequestRepository.findPendingByTenantIdAndPurpose(
                TENANT_ID, SubscriptionPaymentPurpose.INITIAL_ACTIVATION))
                .thenReturn(Optional.of(pending), Optional.empty());
        SubscriptionPaymentRequest paid = buildPendingRequest();
        paid.markPaid("SFK0000001");
        when(paymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.of(paid));
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("0", "Success", "SFK0000001"));

        SubscriptionPaymentRequest result = service.switchToPremium(
                TENANT_ID, PLAN_CODE, "254714931575");

        assertEquals(SubscriptionPaymentRequestStatus.PAID, result.getStatus());
        verify(callbackTransactionService).processSuccessfulCallback(CHECKOUT_ID, "SFK0000001");
        verify(darajaService, never()).initiateSTKPush(any(), any(), any(), any(), any(), any());
    }

    @Test
    void switchRetry_whenPendingRequestResolvesFailed_createsFreshAttempt() {
        stubSwitchBasics();
        SubscriptionPaymentRequest pending = buildPendingRequest();
        when(paymentRequestRepository.findPendingByTenantIdAndPurpose(
                TENANT_ID, SubscriptionPaymentPurpose.INITIAL_ACTIVATION))
                .thenReturn(Optional.of(pending), Optional.empty());
        SubscriptionPaymentRequest failed = buildPendingRequest();
        failed.markFailed("The transaction was cancelled by the user");
        when(paymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.of(failed));
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("1032", "The transaction was cancelled by the user", null));
        when(darajaService.initiateSTKPush(any(), any(), any(), any(), any(), any()))
                .thenReturn("ws_CO_new_1");

        SubscriptionPaymentRequest result = service.switchToPremium(
                TENANT_ID, PLAN_CODE, "254714931575");

        verify(callbackTransactionService).processFailedCallback(
                CHECKOUT_ID, "The transaction was cancelled by the user");
        verify(darajaService).initiateSTKPush(any(), any(), any(), any(), any(), any());
        assertEquals("ws_CO_new_1", result.getMpesaCheckoutRequestId());
    }

    @Test
    void switchRetry_whenPendingRequestStillInFlight_returnsExistingRequestForPolling() {
        stubSwitchBasics();
        SubscriptionPaymentRequest pending = buildPendingRequest();
        when(paymentRequestRepository.findPendingByTenantIdAndPurpose(
                TENANT_ID, SubscriptionPaymentPurpose.INITIAL_ACTIVATION))
                .thenReturn(Optional.of(pending));
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("1037", "Processing", null));

        SubscriptionPaymentRequest result = service.switchToPremium(
                TENANT_ID, PLAN_CODE, "254714931575");

        assertEquals(pending, result);
        assertEquals(SubscriptionPaymentRequestStatus.PENDING, result.getStatus());
        verify(darajaService, never()).initiateSTKPush(any(), any(), any(), any(), any(), any());
    }

    @Test
    void switchRetry_whenPendingRequestStale_expiresItAndCreatesFreshAttempt() {
        stubSwitchBasics();
        SubscriptionPaymentRequest stale = buildPendingRequest(
                Instant.now().minus(subscriptionBillingProperties.getPaymentRequestExpiryMinutes() + 5, ChronoUnit.MINUTES));
        when(paymentRequestRepository.findPendingByTenantIdAndPurpose(
                TENANT_ID, SubscriptionPaymentPurpose.INITIAL_ACTIVATION))
                .thenReturn(Optional.of(stale));
        when(darajaService.querySTKStatus(eq(CHECKOUT_ID), any()))
                .thenReturn(new DarajaService.StkQueryResult("1037", "Processing", null));
        when(darajaService.initiateSTKPush(any(), any(), any(), any(), any(), any()))
                .thenReturn("ws_CO_new_1");

        SubscriptionPaymentRequest result = service.switchToPremium(
                TENANT_ID, PLAN_CODE, "254714931575");

        assertEquals(SubscriptionPaymentRequestStatus.EXPIRED, stale.getStatus());
        verify(paymentRequestRepository).save(stale);
        verify(darajaService).initiateSTKPush(any(), any(), any(), any(), any(), any());
        assertEquals("ws_CO_new_1", result.getMpesaCheckoutRequestId());
    }
}
