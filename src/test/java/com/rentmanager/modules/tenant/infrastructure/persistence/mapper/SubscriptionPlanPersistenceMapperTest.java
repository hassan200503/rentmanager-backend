package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression test: entity -> domain mapping must restore the persistent
 * identity (id + version). Previously toDomain() dropped the id, so plans
 * loaded from the DB reported a null id - breaking the plans list keys
 * ("Subscription plan ID cannot be null" on /subscription/switch).
 */
class SubscriptionPlanPersistenceMapperTest {

    private final SubscriptionPlanPersistenceMapper mapper =
            new SubscriptionPlanPersistenceMapper() {};

    private final UUID planId = UUID.randomUUID();

    private SubscriptionPlanEntity entity;

    @BeforeEach
    void setUp() {
        entity = new SubscriptionPlanEntity();
        entity.setId(planId);
        entity.setVersion(7L);
        entity.setCode("STARTER");
        entity.setName("Starter");
        entity.setDescription("Entry tier");
        entity.setBillingCycle(BillingCycle.MONTHLY);
        entity.setMaxUnits(10);
        entity.setMonthlyPrice(new BigDecimal("2500.00"));
        entity.setYearlyPrice(new BigDecimal("25000.00"));
        entity.setActive(true);
        entity.setSelfService(true);
    }

    @Test
    void toDomain_restoresIdAndVersion() {
        SubscriptionPlan plan = mapper.toDomain(entity);

        assertSame(planId, plan.getId());
        assertEquals(7L, plan.getVersion());
        assertEquals("STARTER", plan.getCode());
        assertEquals("Starter", plan.getName());
        assertEquals(BillingCycle.MONTHLY, plan.getBillingCycle());
        assertEquals(10, plan.getMaxUnits());
        assertEquals(0, plan.getMonthlyPrice().compareTo(new BigDecimal("2500.00")));
        assertEquals(0, plan.getYearlyPrice().compareTo(new BigDecimal("25000.00")));
        assertEquals(true, plan.isActive());
        assertEquals(true, plan.isSelfService());
    }

    @Test
    void toDomain_nullEntity_returnsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void toDomain_unpersistedColumnsAreNull() {
        SubscriptionPlan plan = mapper.toDomain(entity);

        assertNull(plan.getMaxProperties());
        assertNull(plan.getMaxUsers());
        assertNull(plan.getMaxStorageGb());
    }

    @Test
    void toJpaEntity_roundTripsIdentityAndFields() {
        SubscriptionPlan plan = mapper.toDomain(entity);

        SubscriptionPlanEntity back = mapper.toJpaEntity(plan);

        assertNotNull(back);
        assertEquals(planId, back.getId());
        assertEquals(7L, back.getVersion());
        assertEquals("STARTER", back.getCode());
        assertEquals("Starter", back.getName());
        assertEquals(BillingCycle.MONTHLY, back.getBillingCycle());
        assertEquals(10, back.getMaxUnits());
        assertEquals(0, back.getMonthlyPrice().compareTo(new BigDecimal("2500.00")));
        assertEquals(true, back.isActive());
        assertEquals(true, back.isSelfService());
    }
}
