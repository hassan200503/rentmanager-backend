package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.SubscriptionPlanRequest;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform-owner plan retiering: prices must be mutable on the catalog
 * (public pricing serves the mutated values instantly), the code is the
 * immutable identity, and unknown plans fail closed with 404 semantics.
 */
class SubscriptionPlanCommandServiceImplTest {

    private final SubscriptionPlanRepository repository = mock(SubscriptionPlanRepository.class);
    private final SubscriptionPlanCommandServiceImpl service =
            new SubscriptionPlanCommandServiceImpl(repository);

    private final UUID planId = UUID.randomUUID();

    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        plan = SubscriptionPlan.rehydrate(
                planId, 1L, "STARTER", "Starter", "Up to 10 units",
                BillingCycle.MONTHLY, null, 10, null, null,
                new BigDecimal("2500.00"), null, true, true
        );
    }

    private SubscriptionPlanRequest request(
            String name,
            String description,
            BillingCycle billingCycle,
            Integer maxUnits,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice
    ) {
        // SubscriptionPlanRequest exposes getters only (Jackson populates
        // fields via reflection), so the test builds it as an anonymous
        // subclass that overrides the getters.
        return new SubscriptionPlanRequest() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return description; }
            @Override
            public BillingCycle getBillingCycle() { return billingCycle; }
            @Override
            public Integer getMaxUnits() { return maxUnits; }
            @Override
            public BigDecimal getMonthlyPrice() { return monthlyPrice; }
            @Override
            public BigDecimal getYearlyPrice() { return yearlyPrice; }
        };
    }

    @Test
    void update_persistsNewPricingAndDisplayCopy() {
        SubscriptionPlanRequest request = request(
                "Landlord Standard",
                "For single properties and small portfolios",
                BillingCycle.MONTHLY,
                10,
                new BigDecimal("999.00"),
                null
        );

        when(repository.findById(planId)).thenReturn(Optional.of(plan));
        when(repository.save(plan)).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionPlanResponse response = service.update(planId, request);

        assertEquals("Landlord Standard", response.getName());
        assertEquals("Landlord Standard", plan.getName());
        assertEquals("For single properties and small portfolios", plan.getDescription());
        assertEquals(0, plan.getMonthlyPrice().compareTo(new BigDecimal("999.00")));
        assertEquals(BillingCycle.MONTHLY, plan.getBillingCycle());
        assertEquals(10, plan.getMaxUnits());
        assertEquals(0, response.getMonthlyPrice().compareTo(new BigDecimal("999.00")));
        verify(repository).save(plan);
    }

    @Test
    void update_keepsCodeImmutable() {
        SubscriptionPlanRequest request = request(
                "Landlord Standard standalone",
                null,
                BillingCycle.MONTHLY,
                10,
                new BigDecimal("999.00"),
                null
        );

        when(repository.findById(planId)).thenReturn(Optional.of(plan));
        when(repository.save(plan)).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(planId, request);

        assertEquals("STARTER", plan.getCode());
    }

    @Test
    void update_unknownPlanThrowsNotFound() {
        SubscriptionPlanRequest request = request(
                "Landlord Standard standalone", null, BillingCycle.MONTHLY,
                10, new BigDecimal("999.00"), null
        );

        when(repository.findById(planId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(planId, request));
    }

    @Test
    void update_negativePriceRejectedWithoutPersisting() {
        SubscriptionPlanRequest request = request(
                "Landlord Standard standalone", null, BillingCycle.MONTHLY,
                10, new BigDecimal("-5.00"), null
        );

        when(repository.findById(planId)).thenReturn(Optional.of(plan));

        assertThrows(IllegalArgumentException.class, () -> service.update(planId, request));
        verify(repository, never()).save(plan);
    }
}