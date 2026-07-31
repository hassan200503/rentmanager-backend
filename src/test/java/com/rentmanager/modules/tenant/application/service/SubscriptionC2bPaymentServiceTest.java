package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository;
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
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.daraja.C2BPaymentConfirmationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 1 v2 - C2B (Paybill) confirmation matching for the Ratiba
 * autobilling flow: reference + exact fee match extends the paid period
 * from its anchor; everything else fails closed into unmatched_payments.
 * Manual mock() construction per AGENTS.md (no MockitoExtension / @Nested).
 */
class SubscriptionC2bPaymentServiceTest {

    private TenantRepository tenantRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private SubscriptionPaymentRequestRepository paymentRequestRepository;
    private UnmatchedPaymentRepository unmatchedPaymentRepository;

    private SubscriptionC2bPaymentService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final String ACCOUNT_REFERENCE = "T-001";

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        paymentRequestRepository = mock(SubscriptionPaymentRequestRepository.class);
        unmatchedPaymentRepository = mock(UnmatchedPaymentRepository.class);

        service = new SubscriptionC2bPaymentService(
                tenantRepository,
                subscriptionPlanRepository,
                paymentRequestRepository,
                unmatchedPaymentRepository
        );
    }

    // ----------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------

    private Tenant buildCommissionTenant() {
        Tenant tenant = Tenant.create(
                ACCOUNT_REFERENCE, "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        return tenant;
    }

    private Tenant buildPremiumTenant(LocalDate endDate) {
        Tenant tenant = buildCommissionTenant();
        tenant.activatePremiumSubscription(PLAN_ID, LocalDate.now().minusMonths(1), endDate);
        return tenant;
    }

    private SubscriptionPlan buildPlan() {
        SubscriptionPlan plan = SubscriptionPlan.reconstruct(
                "STARTER", "Starter", "desc", BillingCycle.MONTHLY,
                null, 10, null, null, new BigDecimal("2500.00"), null, true, true);
        plan.setId(PLAN_ID);
        return plan;
    }

    private C2BPaymentConfirmationPayload payload(String transId, String amount, String billRef, String msisdn) {
        return new C2BPaymentConfirmationPayload(
                transId, new BigDecimal(amount), billRef, msisdn, "174379");
    }

    // ----------------------------------------------------------------
    // Match -> extend
    // ----------------------------------------------------------------

    @Test
    void matchingPayment_extendsPeriodByOneCycleFromAnchorAndRecordsPaidRequest() {
        LocalDate endDate = LocalDate.now().plusDays(10);
        Tenant tenant = buildPremiumTenant(endDate);
        SubscriptionPlan plan = buildPlan();
        when(tenantRepository.findByTenantCode(ACCOUNT_REFERENCE)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ1"))
                .thenReturn(Optional.empty());
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleConfirmation(payload("RKTQ1", "2500", ACCOUNT_REFERENCE, "254712345678"));

        assertEquals(endDate.plusMonths(1), tenant.getPlanEndDate());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        verify(tenantRepository).save(tenant);
        verify(paymentRequestRepository).save(any(SubscriptionPaymentRequest.class));
        verify(unmatchedPaymentRepository, never()).save(any());
    }

    @Test
    void matchingPayment_duringGrace_clearsGraceAndReturnsToActive() {
        LocalDate endDate = LocalDate.now().minusDays(3);
        Tenant tenant = buildPremiumTenant(endDate);
        tenant.enterPremiumGracePeriod(LocalDate.now().plusDays(4));
        SubscriptionPlan plan = buildPlan();
        when(tenantRepository.findByTenantCode(ACCOUNT_REFERENCE)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ2"))
                .thenReturn(Optional.empty());
        when(paymentRequestRepository.save(any(SubscriptionPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleConfirmation(payload("RKTQ2", "2500", ACCOUNT_REFERENCE, "254712345678"));

        assertEquals(endDate.plusMonths(1), tenant.getPlanEndDate());
        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        assertNull(tenant.getPlanGraceEndsAt());
    }

    @Test
    void duplicateTransId_skipsWithoutChanges() {
        SubscriptionPaymentRequest paid = SubscriptionPaymentRequest.create(
                TENANT_ID, PLAN_ID, new BigDecimal("2500.00"), "254712345678",
                SubscriptionPaymentPurpose.RENEWAL);
        paid.markPaid("RKTQ3");
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ3"))
                .thenReturn(Optional.of(paid));

        service.handleConfirmation(payload("RKTQ3", "2500", ACCOUNT_REFERENCE, "254712345678"));

        verify(tenantRepository, never()).findByTenantCode(anyString());
        verify(unmatchedPaymentRepository, never()).save(any());
        verify(paymentRequestRepository, never()).save(any());
    }

    // ----------------------------------------------------------------
    // Fail closed -> unmatched_payments
    // ----------------------------------------------------------------

    @Test
    void wrongAmount_recordsUnmatchedPayment_failClosed() {
        Tenant tenant = buildPremiumTenant(LocalDate.now().plusDays(10));
        SubscriptionPlan plan = buildPlan();
        when(tenantRepository.findByTenantCode(ACCOUNT_REFERENCE)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ4"))
                .thenReturn(Optional.empty());
        when(unmatchedPaymentRepository.save(any(UnmatchedPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleConfirmation(payload("RKTQ4", "2499", ACCOUNT_REFERENCE, "254712345678"));

        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
        verify(tenantRepository, never()).save(any());
        verify(paymentRequestRepository, never()).save(any());
        verify(unmatchedPaymentRepository).save(argThat(u ->
                u.getTenantId().equals(TENANT_ID)
                        && u.getAmount().compareTo(new BigDecimal("2499.00")) == 0
                        && u.getResultDesc().contains("Amount mismatch")));
    }

    @Test
    void unknownReference_recordsUnmatchedPaymentWithNullTenant() {
        when(tenantRepository.findByTenantCode("NOPE123")).thenReturn(Optional.empty());
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ5"))
                .thenReturn(Optional.empty());
        when(unmatchedPaymentRepository.save(any(UnmatchedPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleConfirmation(payload("RKTQ5", "2500", "NOPE123", "254712345678"));

        verify(unmatchedPaymentRepository).save(argThat(u ->
                u.getTenantId() == null
                        && "NOPE123".equals(u.getAccountReference())
                        && u.getResultDesc().contains("Unknown account reference")));
        verifyNoInteractions(subscriptionPlanRepository);
    }

    @Test
    void nonPremiumTenant_recordsUnmatchedPayment() {
        Tenant tenant = buildCommissionTenant();
        when(tenantRepository.findByTenantCode(ACCOUNT_REFERENCE)).thenReturn(Optional.of(tenant));
        when(paymentRequestRepository.findByMpesaReceiptNumber("RKTQ6"))
                .thenReturn(Optional.empty());
        when(unmatchedPaymentRepository.save(any(UnmatchedPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleConfirmation(payload("RKTQ6", "2500", ACCOUNT_REFERENCE, "254712345678"));

        verify(unmatchedPaymentRepository).save(argThat(u ->
                u.getTenantId().equals(TENANT_ID)
                        && u.getResultDesc().contains("not on premium billing")));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void missingTransId_ignored() {
        service.handleConfirmation(new C2BPaymentConfirmationPayload(
                "  ", new BigDecimal("2500.00"), ACCOUNT_REFERENCE, "254712345678", "174379"));

        verifyNoInteractions(tenantRepository);
        verifyNoInteractions(unmatchedPaymentRepository);
    }
}
