package com.rentmanager.modules.tenant.infrastructure.daraja;

import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.config.SubscriptionBillingProperties;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 1 dual revenue model - subscription payment callbacks.
 * Manual mock() construction per AGENTS.md (no MockitoExtension / @Nested).
 */
class SubscriptionPaymentCallbackTransactionServiceTest {

    private SubscriptionPaymentRequestRepository paymentRequestRepository;
    private TenantRepository tenantRepository;
    private SubscriptionBillingProperties properties;
    private EntityManager entityManager;

    private SubscriptionPaymentCallbackTransactionService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final String CHECKOUT_ID = "ws_CO_sub_1";
    private static final String RECEIPT = "NSU0000001";

    @BeforeEach
    void setUp() {
        paymentRequestRepository = mock(SubscriptionPaymentRequestRepository.class);
        tenantRepository = mock(TenantRepository.class);
        properties = new SubscriptionBillingProperties();
        properties.setGraceDays(7);
        entityManager = mock(EntityManager.class);

        service = new SubscriptionPaymentCallbackTransactionService(
                paymentRequestRepository, tenantRepository, properties, entityManager
        );
    }

    // ----------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------

    private SubscriptionPlan buildPlan() {
        return SubscriptionPlan.reconstruct(
                "STARTER", "Starter", "desc", BillingCycle.MONTHLY,
                null, 10, null, null, new BigDecimal("2500.00"), null, true, true);
    }

    private Tenant buildCommissionTenant() {
        Tenant tenant = Tenant.create(
                "T-001", "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private Tenant buildPremiumTenant(LocalDate endDate) {
        Tenant tenant = buildCommissionTenant();
        tenant.activatePremiumSubscription(PLAN_ID, LocalDate.now().minusMonths(1), endDate);
        return tenant;
    }

    private SubscriptionPaymentRequest buildPendingRequest(SubscriptionPaymentPurpose purpose) {
        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.create(
                TENANT_ID, PLAN_ID, new BigDecimal("2500.00"), "+254712345678", purpose);
        request.attachCheckoutRequestId(CHECKOUT_ID);
        return request;
    }

    private void stubRequest(SubscriptionPaymentRequest request) {
        when(paymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.of(request));
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ----------------------------------------------------------------
    // Successful callbacks
    // ----------------------------------------------------------------

    @Test
    void successfulInitialActivation_switchesTenantToPremium() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
        stubRequest(request);
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(SubscriptionPaymentRequestStatus.PAID, request.getStatus());
        assertEquals(RECEIPT, request.getMpesaReceiptNumber());
        assertEquals(BillingMode.PREMIUM_MONTHLY, tenant.getBillingMode());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        assertEquals(LocalDate.now(), tenant.getPlanStartDate());
        assertEquals(LocalDate.now().plusMonths(1), tenant.getPlanEndDate());
        assertTrue(tenant.isPlanAutoRenew());
        verify(tenantRepository).save(tenant);
        verify(entityManager).flush();
    }

    @Test
    void successfulRenewal_extendsPeriodByOneMonthAndClearsGrace() {
        LocalDate currentEnd = LocalDate.now().minusDays(2);
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        stubRequest(request);
        Tenant tenant = buildPremiumTenant(currentEnd);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(currentEnd.plusMonths(1), tenant.getPlanEndDate());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        assertNull(tenant.getPlanGraceEndsAt());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void duplicateSuccessfulCallback_whenAlreadyPaid_skips() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
        request.markPaid(RECEIPT);
        stubRequest(request);
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        verify(paymentRequestRepository, never()).save(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void lateSuccessAfterFailureCallback_isStillApplied() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
        request.markFailed("User cancelled");
        stubRequest(request);
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(SubscriptionPaymentRequestStatus.PAID, request.getStatus());
        assertEquals(BillingMode.PREMIUM_MONTHLY, tenant.getBillingMode());
    }

    @Test
    void lateSuccessAfterExpirySweep_isStillApplied() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        request.markExpired("No M-Pesa callback received");
        stubRequest(request);
        Tenant tenant = buildPremiumTenant(LocalDate.now().minusDays(2));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(SubscriptionPaymentRequestStatus.PAID, request.getStatus());
        assertEquals(LocalDate.now().minusDays(2).plusMonths(1), tenant.getPlanEndDate());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
    }

    @Test
    void successfulCallback_unknownCheckoutId_throwsResourceNotFound() {
        when(paymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT));
    }

    // ----------------------------------------------------------------
    // Failed callbacks
    // ----------------------------------------------------------------

    @Test
    void failedInitialActivation_leavesTenantOnCommission() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
        stubRequest(request);
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processFailedCallback(CHECKOUT_ID, "Request cancelled by user");

        assertEquals(SubscriptionPaymentRequestStatus.FAILED, request.getStatus());
        assertEquals("Request cancelled by user", request.getFailureReason());
        assertEquals(BillingMode.COMMISSION, tenant.getBillingMode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void failedRenewal_entersGracePeriod() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        stubRequest(request);
        Tenant tenant = buildPremiumTenant(LocalDate.now().minusDays(1));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processFailedCallback(CHECKOUT_ID, "Request cancelled by user");

        assertEquals(SubscriptionStatus.GRACE_PERIOD, tenant.getSubscriptionStatus());
        assertEquals(LocalDate.now().plusDays(7), tenant.getPlanGraceEndsAt());
        assertTrue(tenant.isPremiumBilling());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void failedRenewal_unknownTenant_graceSkipped() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        stubRequest(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.processFailedCallback(CHECKOUT_ID, "Timeout"));
    }

    @Test
    void failedRenewal_nonPremiumTenant_graceSkipped() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        stubRequest(request);
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processFailedCallback(CHECKOUT_ID, "Timeout");

        assertEquals(BillingMode.COMMISSION, tenant.getBillingMode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void duplicateFailureCallback_whenAlreadyFailed_skips() {
        SubscriptionPaymentRequest request = buildPendingRequest(SubscriptionPaymentPurpose.RENEWAL);
        request.markFailed("First failure");
        stubRequest(request);
        Tenant tenant = buildPremiumTenant(LocalDate.now().minusDays(1));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        service.processFailedCallback(CHECKOUT_ID, "Second failure");

        verify(tenantRepository, never()).save(any());
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void failedCallback_unknownCheckoutId_ignored() {
        when(paymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.processFailedCallback(CHECKOUT_ID, "Reason"));
    }
}
