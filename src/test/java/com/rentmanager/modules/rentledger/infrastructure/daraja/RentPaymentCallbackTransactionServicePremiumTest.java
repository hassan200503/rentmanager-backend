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
    void unresolvableTenant_treatedAsCommission() {
        RentPaymentRequest request = buildPendingRentPayment();
        RentTransaction transaction = stubSuccessfulFlow(request);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(commissionPolicyService.getActiveRate(TENANT_ID)).thenReturn(new BigDecimal("3.00"));

        var result = service.processSuccessfulCallback(CHECKOUT_ID, RECEIPT);

        assertEquals(new BigDecimal("45.00"), result.commissionAmount());
        verify(transaction).applyCommission(any(), any(), any());
    }
}
