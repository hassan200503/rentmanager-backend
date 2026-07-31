package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderFrequency;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionStandingOrderRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
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

import org.mockito.ArgumentCaptor;

/**
 * Phase 1 v2 - merchant-initiated M-Pesa Ratiba standing-order onboarding.
 * Feature-flagged via daraja.ratiba-enabled; the landlord consents via the
 * NI push, and the async creation callback resolves PENDING_AUTHORIZATION
 * to ACTIVE/FAILED. Manual mock() per AGENTS.md (no MockitoExtension).
 */
class RatibaStandingOrderServiceTest {

    private TenantRepository tenantRepository;
    private SubscriptionPlanRepository subscriptionPlanRepository;
    private SubscriptionStandingOrderRepository standingOrderRepository;
    private DarajaService darajaService;
    private DarajaProperties darajaProperties;

    private RatibaStandingOrderService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final String ACCOUNT_REFERENCE = "T-001";
    private static final String CALLBACK_URL =
            "https://api.example.com/api/v1/public/subscription-billing/ratiba/callback";

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        subscriptionPlanRepository = mock(SubscriptionPlanRepository.class);
        standingOrderRepository = mock(SubscriptionStandingOrderRepository.class);
        darajaService = mock(DarajaService.class);
        darajaProperties = new DarajaProperties();
        darajaProperties.setConsumerKey("ck");
        darajaProperties.setConsumerSecret("cs");
        darajaProperties.setBusinessShortCode("174379");
        darajaProperties.setPasskey("pk");
        darajaProperties.setRatibaEnabled(true);
        darajaProperties.setRatibaCallbackUrl(CALLBACK_URL);

        service = new RatibaStandingOrderService(
                tenantRepository,
                subscriptionPlanRepository,
                standingOrderRepository,
                darajaService,
                darajaProperties
        );
    }

    // ----------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------

    private Tenant buildPremiumTenant(LocalDate endDate) {
        Tenant tenant = Tenant.create(
                ACCOUNT_REFERENCE, "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
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

    private void stubDarajaOk(String responseRefId) {
        when(darajaService.createStandingOrder(
                eq("+254712345678"),
                eq(new BigDecimal("2500.00")),
                eq(ACCOUNT_REFERENCE),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CALLBACK_URL)))
                .thenReturn(responseRefId);
    }

    // ----------------------------------------------------------------
    // createStandingOrder
    // ----------------------------------------------------------------

    @Test
    void createStandingOrder_featureDisabled_throwsBusinessException() {
        darajaProperties.setRatibaEnabled(false);
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(buildPremiumTenant(LocalDate.now().plusDays(20))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createStandingOrder(TENANT_ID));
        assertEquals(ErrorCode.SUBSCRIPTION_RATIBA_UNAVAILABLE, ex.getErrorCode());
        verifyNoInteractions(darajaService);
    }

    @Test
    void createStandingOrder_notPremium_throwsBusinessException() {
        Tenant tenant = Tenant.create(
                ACCOUNT_REFERENCE, "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createStandingOrder(TENANT_ID));
        assertEquals(ErrorCode.SUBSCRIPTION_NOT_PREMIUM, ex.getErrorCode());
    }

    @Test
    void createStandingOrder_happyPath_createsOrderInitiatesDarajaCallAndAttachesRef() {
        LocalDate planEnd = LocalDate.now().plusDays(20);
        Tenant tenant = buildPremiumTenant(planEnd);
        SubscriptionPlan plan = buildPlan();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(standingOrderRepository.save(any(SubscriptionStandingOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDarajaOk("ref-123");

        SubscriptionStandingOrder order = service.createStandingOrder(TENANT_ID);

        assertEquals(StandingOrderStatus.PENDING_AUTHORIZATION, order.getStatus());
        assertEquals(ACCOUNT_REFERENCE, order.getAccountReference());
        assertEquals(new BigDecimal("2500.00"), order.getAmount());
        assertEquals(planEnd, order.getStartDate());
        assertEquals(planEnd.plusYears(1), order.getEndDate());
        assertEquals("ref-123", order.getRatibaResponseRefId());
        verify(darajaService).createStandingOrder(
                eq("+254712345678"),
                eq(new BigDecimal("2500.00")),
                eq(ACCOUNT_REFERENCE),
                eq(planEnd),
                eq(planEnd.plusYears(1)),
                eq(CALLBACK_URL));
        verify(standingOrderRepository, times(2)).save(any(SubscriptionStandingOrder.class));
    }

    @Test
    void createStandingOrder_tenantCodeTooLong_throwsBusinessException() {
        Tenant tenant = Tenant.create(
                "ABCDEFGHIJKLM", "Test Landlord", "test-landlord",
                "landlord@example.com", "+254712345678", TenantType.STANDARD);
        tenant.setId(TENANT_ID);
        tenant.activatePremiumSubscription(PLAN_ID, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(20));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createStandingOrder(TENANT_ID));
        assertEquals(ErrorCode.SUBSCRIPTION_RATIBA_UNAVAILABLE, ex.getErrorCode());
        verifyNoInteractions(darajaService);
    }

    @Test
    void createStandingOrder_darajaRejects_marksOrderFailedAndRethrows() {
        Tenant tenant = buildPremiumTenant(LocalDate.now().plusDays(20));
        SubscriptionPlan plan = buildPlan();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(subscriptionPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(standingOrderRepository.save(any(SubscriptionStandingOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(darajaService.createStandingOrder(anyString(), any(), anyString(), any(), any(), anyString()))
                .thenThrow(new DarajaException("no responseRefID in response"));

        assertThrows(DarajaException.class, () -> service.createStandingOrder(TENANT_ID));

        ArgumentCaptor<SubscriptionStandingOrder> captor =
                ArgumentCaptor.forClass(SubscriptionStandingOrder.class);
        verify(standingOrderRepository, times(2)).save(captor.capture());
        SubscriptionStandingOrder finalSaved = captor.getAllValues().get(1);
        assertEquals(StandingOrderStatus.FAILED, finalSaved.getStatus());
        assertTrue(finalSaved.getFailureReason().contains("no responseRefID"));
    }

    // ----------------------------------------------------------------
    // handleCreationCallback
    // ----------------------------------------------------------------

    @Test
    void handleCreationCallback_success_marksOrderActive() {
        SubscriptionStandingOrder order = SubscriptionStandingOrder.create(
                TENANT_ID, ACCOUNT_REFERENCE, new BigDecimal("2500.00"),
                StandingOrderFrequency.MONTHLY,
                LocalDate.now(), LocalDate.now().plusYears(1));
        order.setId(UUID.randomUUID());
        order.attachResponseRefId("ref-123");
        when(standingOrderRepository.findByRatibaResponseRefId("ref-123"))
                .thenReturn(Optional.of(order));
        when(standingOrderRepository.save(any(SubscriptionStandingOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleCreationCallback("ref-123", true, "SO_ORD_1", null);

        assertEquals(StandingOrderStatus.ACTIVE, order.getStatus());
        assertEquals("SO_ORD_1", order.getRatibaTransactionId());
        verify(standingOrderRepository).save(order);
    }

    @Test
    void handleCreationCallback_failure_marksOrderFailed() {
        SubscriptionStandingOrder order = SubscriptionStandingOrder.create(
                TENANT_ID, ACCOUNT_REFERENCE, new BigDecimal("2500.00"),
                StandingOrderFrequency.MONTHLY,
                LocalDate.now(), LocalDate.now().plusYears(1));
        order.setId(UUID.randomUUID());
        order.attachResponseRefId("ref-456");
        when(standingOrderRepository.findByRatibaResponseRefId("ref-456"))
                .thenReturn(Optional.of(order));
        when(standingOrderRepository.save(any(SubscriptionStandingOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleCreationCallback("ref-456", false, null, "customer declined the PIN prompt");

        assertEquals(StandingOrderStatus.FAILED, order.getStatus());
        assertTrue(order.getFailureReason().contains("declined"));
    }

    @Test
    void handleCreationCallback_unknownRef_ignored() {
        when(standingOrderRepository.findByRatibaResponseRefId("ref-unknown"))
                .thenReturn(Optional.empty());

        service.handleCreationCallback("ref-unknown", true, "SO_X", null);

        verify(standingOrderRepository, never()).save(any());
    }
}
