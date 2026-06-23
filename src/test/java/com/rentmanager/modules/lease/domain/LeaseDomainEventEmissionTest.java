package com.rentmanager.modules.lease.domain;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.event.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LeaseDomainEventEmissionTest {

    private Lease createLease() {
        return Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-EVT-001",
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(6),
                new BigDecimal("10000"),
                new BigDecimal("20000"),
                new BigDecimal("500"),
                7,
                false
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractEvents(Lease lease) {
        try {
            Class<?> clazz = lease.getClass();

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField("domainEvents");
                    field.setAccessible(true);
                    return (List<Object>) field.get(lease);
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }

            fail("No domainEvents field found in AggregateRoot");
            return List.of();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldEmitLeaseCreatedEvent() {

        Lease lease = createLease();

        List<Object> events = extractEvents(lease);

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseCreatedEvent)
        );
    }

    @Test
    void shouldEmitActivationEvent() {

        Lease lease = createLease();

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        List<Object> events = extractEvents(lease);

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseActivatedEvent)
        );
    }

    @Test
    void shouldEmitTerminationEvent() {

        Lease lease = createLease();

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        lease.terminate(
                TerminationType.TENANT_REQUEST,
                "exit",
                "SYSTEM",
                lease.getTenantId()
        );

        List<Object> events = extractEvents(lease);

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseTerminatedEvent)
        );
    }

    @Test
    void shouldEmitRenewalEvent() {

        Lease lease = createLease();

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        lease.renew(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(12),
                lease.getTenantId(),
                "SYSTEM"
        );
        List<Object> events = extractEvents(lease);

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseRenewedEvent)
        );
    }
}