package com.rentmanager.modules.tenant.application.scheduler;

import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.config.SubscriptionBillingProperties;
import com.rentmanager.modules.tenant.infrastructure.daraja.SubscriptionPaymentCallbackTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 1 v2 (Ratiba autobilling) - subscription expiry sweeps. The sweep
 * NEVER initiates payments (collections arrive via C2B / Pay-Now callbacks);
 * it only advances state: period ended unpaid -> GRACE_PERIOD, then
 * grace ended unpaid / downgrade period ended -> revert to COMMISSION.
 * Manual mock() construction per AGENTS.md (no MockitoExtension / @Nested).
 */
class SubscriptionExpirySweepServiceTest {

    private TenantRepository tenantRepository;
    private SubscriptionPaymentRequestRepository paymentRequestRepository;
    private SubscriptionPaymentCallbackTransactionService callbackTxService;
    private SubscriptionBillingProperties properties;

    private SubscriptionExpirySweepService sweepService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        paymentRequestRepository = mock(SubscriptionPaymentRequestRepository.class);
        callbackTxService = mock(SubscriptionPaymentCallbackTransactionService.class);
        properties = new SubscriptionBillingProperties();
        properties.setGraceDays(7);
        properties.setPaymentRequestExpiryMinutes(30);

        sweepService = new SubscriptionExpirySweepService(
                tenantRepository,
                paymentRequestRepository,
                callbackTxService,
                properties
        );
    }

    // ----------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------

    private Tenant buildTenant() {
        Tenant tenant = Tenant.create(
                "T-001", "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private Tenant buildPremiumTenant(LocalDate endDate) {
        Tenant tenant = buildTenant();
        tenant.activatePremiumSubscription(PLAN_ID, LocalDate.now().minusMonths(1), endDate);
        return tenant;
    }

    // ----------------------------------------------------------------
    // Grace transitions (period ended, no Ratiba/C2B payment received)
    // ----------------------------------------------------------------

    @Test
    void enterGraceForExpiredSubscriptions_periodEndedNoPayment_entersGraceAnchoredAtPeriodEnd() {
        LocalDate planEndDate = LocalDate.now().minusDays(2);
        Tenant tenant = buildPremiumTenant(planEndDate);
        when(tenantRepository.findPremiumRenewalsDue(LocalDate.now())).thenReturn(List.of(tenant));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.enterGraceForExpiredSubscriptions();

        assertEquals(SubscriptionStatus.GRACE_PERIOD, tenant.getSubscriptionStatus());
        assertEquals(planEndDate.plusDays(properties.getGraceDays()), tenant.getPlanGraceEndsAt());
        assertTrue(tenant.isPremiumBilling());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void enterGraceForExpiredSubscriptions_noCandidates_noop() {
        when(tenantRepository.findPremiumRenewalsDue(LocalDate.now()))
                .thenReturn(Collections.emptyList());

        sweepService.enterGraceForExpiredSubscriptions();

        verify(tenantRepository, never()).save(any());
    }

    @Test
    void graceOne_periodStillActive_skips() {
        Tenant tenant = buildPremiumTenant(LocalDate.now().plusDays(10));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.graceOne(TENANT_ID);

        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void graceOne_nonPremiumTenant_skips() {
        Tenant tenant = buildTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.graceOne(TENANT_ID);

        verify(tenantRepository, never()).save(any());
    }

    // ----------------------------------------------------------------
    // Reverts (voluntary non-renewal + overdue grace)
    // ----------------------------------------------------------------

    @Test
    void revertNonRenewingSubscriptions_periodEnded_revertsToCommission() {
        Tenant tenant = buildPremiumTenant(LocalDate.now().minusDays(1));
        tenant.markPremiumNonRenewal();
        when(tenantRepository.findPremiumNonRenewalsDue(LocalDate.now())).thenReturn(List.of(tenant));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.revertNonRenewingSubscriptions();

        assertFalse(tenant.isPremiumBilling());
        assertEquals(BillingMode.COMMISSION, tenant.getBillingMode());
        assertEquals(SubscriptionStatus.LAPSED, tenant.getSubscriptionStatus());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void revertOverdueGraceSubscriptions_graceEndedUnpaid_revertsToCommission() {
        Tenant tenant = buildPremiumTenant(LocalDate.now().minusDays(10));
        tenant.enterPremiumGracePeriod(LocalDate.now().minusDays(1));
        when(tenantRepository.findPremiumGraceOverdue(LocalDate.now())).thenReturn(List.of(tenant));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.revertOverdueGraceSubscriptions();

        assertFalse(tenant.isPremiumBilling());
        assertEquals(BillingMode.COMMISSION, tenant.getBillingMode());
        assertEquals(SubscriptionStatus.LAPSED, tenant.getSubscriptionStatus());
        assertNull(tenant.getPlanGraceEndsAt());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void revertOne_nonPremiumTenant_skips() {
        Tenant tenant = buildTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        sweepService.revertOne(TENANT_ID);

        verify(tenantRepository, never()).save(any());
    }

    // ----------------------------------------------------------------
    // Stale Pay-Now payment requests
    // ----------------------------------------------------------------

    @Test
    void expireStalePaymentRequests_initialActivation_expiresWithoutGrace() {
        SubscriptionPaymentRequest stale = SubscriptionPaymentRequest.create(
                TENANT_ID, PLAN_ID, new BigDecimal("2500.00"), "+254712345678",
                SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
        stale.setId(UUID.randomUUID());
        when(paymentRequestRepository.findByStatusAndCreatedAtBefore(
                eq(SubscriptionPaymentRequestStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));
        when(paymentRequestRepository.findById(stale.getId())).thenReturn(Optional.of(stale));
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        sweepService.expireStalePaymentRequests();

        assertEquals(SubscriptionPaymentRequestStatus.EXPIRED, stale.getStatus());
        verify(callbackTxService, never()).enterGracePeriod(any());
    }

    @Test
    void expireStalePaymentRequests_renewal_entersGracePeriod() {
        SubscriptionPaymentRequest stale = SubscriptionPaymentRequest.create(
                TENANT_ID, PLAN_ID, new BigDecimal("2500.00"), "+254712345678",
                SubscriptionPaymentPurpose.RENEWAL);
        stale.setId(UUID.randomUUID());
        when(paymentRequestRepository.findByStatusAndCreatedAtBefore(
                eq(SubscriptionPaymentRequestStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));
        when(paymentRequestRepository.findById(stale.getId())).thenReturn(Optional.of(stale));
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        sweepService.expireStalePaymentRequests();

        assertEquals(SubscriptionPaymentRequestStatus.EXPIRED, stale.getStatus());
        verify(callbackTxService).enterGracePeriod(TENANT_ID);
    }

    @Test
    void expireOne_noLongerPending_skips() {
        SubscriptionPaymentRequest paid = SubscriptionPaymentRequest.create(
                TENANT_ID, PLAN_ID, new BigDecimal("2500.00"), "+254712345678",
                SubscriptionPaymentPurpose.RENEWAL);
        paid.setId(UUID.randomUUID());
        paid.markPaid("NSU0000009");
        when(paymentRequestRepository.findById(paid.getId())).thenReturn(Optional.of(paid));

        sweepService.expireOne(paid.getId());

        verify(paymentRequestRepository, never()).save(any());
        verify(callbackTxService, never()).enterGracePeriod(any());
    }
}
