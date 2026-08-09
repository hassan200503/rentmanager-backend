package com.rentmanager.modules.tenant.application.query.service;

import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Public pricing projection: only ACTIVE plans may be served to the
 * unauthenticated landing surface. Deactivated plans must disappear the
 * moment the platform owner deactivates them.
 */
class SubscriptionPlanQueryServiceImplTest {

    private final SubscriptionPlanRepository repository = mock(SubscriptionPlanRepository.class);
    private final SubscriptionPlanQueryServiceImpl service =
            new SubscriptionPlanQueryServiceImpl(repository);

    @Test
    void getActive_returnsAllActivePlans() {
        SubscriptionPlan active = SubscriptionPlan.rehydrate(
                UUID.randomUUID(), 0L, "STARTER", "Starter", "Up to 10 units",
                BillingCycle.MONTHLY, null, 10, null, null,
                new BigDecimal("2500.00"), null, true, true
        );
        SubscriptionPlan inactive = SubscriptionPlan.rehydrate(
                UUID.randomUUID(), 0L, "LEGACY", "Legacy", "Discontinued",
                BillingCycle.MONTHLY, null, 5, null, null,
                new BigDecimal("100.00"), null, false, true
        );

        when(repository.findAllActive()).thenReturn(List.of(active, inactive));

        List<SubscriptionPlanResponse> result = service.getActive();

        assertEquals(2, result.size());
        assertEquals("STARTER", result.get(0).getCode());
        assertEquals("LEGACY", result.get(1).getCode());
    }

    @Test
    void getActive_emptyCatalog() {
        when(repository.findAllActive()).thenReturn(List.of());

        List<SubscriptionPlanResponse> result = service.getActive();

        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }
}