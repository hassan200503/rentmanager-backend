package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
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
 * Phase 1 dual revenue model - the premium bypass in the rent payment
 * commission flow. Manual mock() construction per AGENTS.md.
 *
 * Regression contract:
 * - COMMISSION landlord: rate is whatever the active commission policy
 *   holds (3.00% pinned here) - mechanics unchanged, commission line +
 *   net = gross - commission.
 * - PREMIUM_MONTHLY landlord (incl. GRACE_PERIOD): zero commission line,
 *   net = gross (100% B2C).
 * - Unresolvable tenant: fail-closed, treated as COMMISSION.
 */
class RentPaymentCallbackTransactionServicePremiumTest {

    private RentPaymentRequestRepository rentPaymentRequestRepository;
    private RentLedgerApplicationService rentLedgerApplicationService;
    private RentTransactionRepository rentTransactionRepository;
    private CommissionPolicyService commissionPolicyService;
    private DisbursementRepository disbursementRepository;
    private EntityManager entityManager;
    private TenantRepository tenantRepository;

    private RentPaymentCallbackTransactionService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String CHECKOUT_ID = "ws_CO_rent_prem_1";
    private static final String RECEIPT = "NLJ7RT61SV";
    private static final BigDecimal GROSS = new BigDecimal("1500.00");

    @BeforeEach
    void setUp() {
        rentPaymentRequestRepository = mock(RentPaymentRequestRepository.class);
        rentLedgerApplicationService = mock(RentLedgerApplicationService.class);
        rentTransactionRepository = mock(RentTransactionRepository.class);
        commissionPolicyService = mock(CommissionPolicyService.class);
        disbursementRepository = mock(DisbursementRepository.class);
        entityManager = mock(EntityManager.class);
        tenantRepository = mock(TenantRepository.class);

        service = new RentPaymentCallbackTransactionService(
                rentPaymentRequestRepository,
                // Money that arrives but cannot be applied is parked here
                // rather than lost — see parkUnappliedPayment.
                mock(com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository.class),
                rentLedgerApplicationService,
                rentTransactionRepository,
                commissionPolicyService,
                disbursementRepository,
                entityManager,
                tenantRepository
        );
    }

    // ----------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------

    private RentPaymentRequest buildPendingRentPayment() {
        RentPaymentRequest request = RentPaymentRequest.create(
                TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), GROSS);
        request.attachCheckoutRequestId(CHECKOUT_ID);
        return request;
    }

    private Tenant buildLandlord(BillingMode billingMode) {
        Tenant tenant = Tenant.create(
                "T-001", "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        // Commission only exists where the platform actually holds the money.
        // Since V89, Tenant.create() defaults to CollectionMode.DIRECT — rent
        // settles into the landlord's own paybill — and a DIRECT landlord
        // correctly has no commission deducted and nothing to disburse. Every
        // test in this class is about the custody path, so it must say so
        // rather than rely on a default that no longer means custody.
        org.springframework.test.util.ReflectionTestUtils.setField(
                tenant, "collectionMode",
                com.rentmanager.modules.tenant.domain.enums.CollectionMode.PLATFORM_CUSTODY);
        if (billingMode == BillingMode.PREMIUM_MONTHLY) {
            tenant.activatePremiumSubscription(UUID.randomUUID(), LocalDate.now().minusMonths(1), LocalDate.now().plusDays(15));
        }
        return tenant;
    }

    private RentTransaction stubSuccessfulFlow(RentPaymentRequest request) {
        when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(CHECKOUT_ID))
                .thenReturn(Optional.of(request));
        when(rentPaymentRequestRepository.save(any(RentPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RentTransaction transaction = mock(RentTransaction.class);
        when(rentTransactionRepository.findByExternalReference(TENANT_ID, RECEIPT))
                .thenReturn(Optional.of(transaction));
        return transaction;
    }

    // ----------------------------------------------------------------
    // COMMISSION mode (regression: mechanics unchanged, 3% pinned)
    // ----------------------------------------------------------------

    @Test
    void commissionTenant_withPolicyRate_appliesCommissionAndNet() {
        RentPaymentRequest request = buildPendingRentPayment();
        RentTransaction transaction = stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildLandlord(BillingMode.COMMISSION)));
        when(commissionPolicyService.getActiveRate(TENANT_ID)).thenReturn(new BigDecimal("3.00"));

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(new BigDecimal("3.00"), result.commissionRatePercent());
        assertEquals(new BigDecimal("45.00"), result.commissionAmount());
        assertEquals(new BigDecimal("1455.00"), result.netAmount());
        verify(transaction).applyCommission(new BigDecimal("3.00"), new BigDecimal("45.00"), new BigDecimal("1455.00"));
        verify(rentTransactionRepository).save(transaction);
    }

    @Test
    void commissionTenant_withoutPolicyRate_noCommissionNoNet() {
        RentPaymentRequest request = buildPendingRentPayment();
        RentTransaction transaction = stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildLandlord(BillingMode.COMMISSION)));
        when(commissionPolicyService.getActiveRate(TENANT_ID)).thenReturn(null);

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertNull(result.commissionRatePercent());
        assertNull(result.commissionAmount());
        assertNull(result.netAmount());
        verify(transaction, never()).applyCommission(any(), any(), any());
        verify(rentTransactionRepository, never()).save(any());
    }

    // ----------------------------------------------------------------
    // PREMIUM_MONTHLY mode (Phase 1: zero commission, 100% net)
    // ----------------------------------------------------------------

    @Test
    void premiumTenant_zeroCommission_fullNetToLandlord() {
        RentPaymentRequest request = buildPendingRentPayment();
        RentTransaction transaction = stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildLandlord(BillingMode.PREMIUM_MONTHLY)));

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertNull(result.commissionRatePercent());
        assertNull(result.commissionAmount());
        assertEquals(GROSS, result.netAmount());
        verify(transaction, never()).applyCommission(any(), any(), any());
        verify(rentTransactionRepository, never()).save(any());
        verify(commissionPolicyService, never()).getActiveRate(any());
    }

    @Test
    void premiumTenantInGracePeriod_stillZeroCommission_fullNet() {
        RentPaymentRequest request = buildPendingRentPayment();
        RentTransaction transaction = stubSuccessfulFlow(request);
        Tenant tenant = buildLandlord(BillingMode.PREMIUM_MONTHLY);
        tenant.enterPremiumGracePeriod(LocalDate.now().plusDays(7));
        assertEquals(SubscriptionStatus.GRACE_PERIOD, tenant.getSubscriptionStatus());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(GROSS, result.netAmount());
        assertNull(result.commissionRatePercent());
        verify(transaction, never()).applyCommission(any(), any(), any());
    }

    // ----------------------------------------------------------------
    // Fail-closed
    // ----------------------------------------------------------------

    @Test
    void unresolvableTenant_treatedAsDirect_soNoCommissionAndNothingToDisburse() {
        RentPaymentRequest request = buildPendingRentPayment();
        stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        // V89 REVERSED the fail-closed direction here, deliberately.
        //
        // This test previously asserted that an unresolvable tenant was
        // treated as COMMISSION — correct while custody was the default,
        // because the safe assumption was "we are holding this money".
        // That assumption no longer holds. Under DIRECT the money went
        // straight to the landlord, so treating an unknown tenant as
        // COMMISSION would deduct a cut from funds the platform never
        // received and then hand a net amount to the B2C path to pay out
        // of a float that has no such money in it.
        //
        // Between two guesses, the safe one is now DIRECT: take nothing,
        // pay out nothing, and let the ledger record the payment.
        org.assertj.core.api.Assertions.assertThat(result.commissionAmount()).isNull();
        org.assertj.core.api.Assertions.assertThat(result.netAmount()).isNull();
        verifyNoInteractions(commissionPolicyService);
    }

    /**
     * The behaviour V89 introduced, stated positively.
     *
     * <p>A DIRECT landlord's rent never reaches the platform, so there is
     * nothing to take a commission from and nothing to pay out. netAmount
     * must stay null: it is what drives the downstream B2C, and a non-null
     * value here would attempt a disbursement against a float that never
     * received the funds.
     */
    @Test
    void directCollection_takesNoCommissionAndLeavesNothingToDisburse() {
        Tenant tenant = Tenant.create(
                "T-002", "Direct Landlord", "direct-landlord",
                "direct@example.com", "+254712345679", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        // No reflection needed — DIRECT is the default, which is the point.

        RentPaymentRequest request = buildPendingRentPayment();
        stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        org.assertj.core.api.Assertions.assertThat(result.commissionAmount())
                .as("nothing to take a cut of — the money went straight to the landlord")
                .isNull();
        org.assertj.core.api.Assertions.assertThat(result.netAmount())
                .as("null netAmount is what stops initiateB2CIfNeeded from paying out "
                        + "money the platform never received")
                .isNull();
        verifyNoInteractions(commissionPolicyService);
    }
}
