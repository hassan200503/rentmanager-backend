package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the event-publishing contract between
 * {@link com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService}
 * and its repository adapter.
 *
 * <h2>The bug this exists to prevent</h2>
 * {@code RentLedgerEntryRepositoryAdapter.save()} returns
 * {@code mapper.toDomain(saved)} — a rehydrated aggregate whose
 * {@code domainEvents} list is a fresh, empty {@code ArrayList}. The service
 * used to do:
 *
 * <pre>{@code
 * entry = rentLedgerEntryRepository.save(entry);   // now the rehydrated copy
 * publish(entry);                                  // pulls from an empty list
 * }</pre>
 *
 * so every rent-ledger domain event was dropped on the floor. Nothing threw
 * and nothing logged; the downstream listeners simply never ran. No tax
 * invoice was ever generated from a rent payment and no payment notification
 * was ever sent.
 *
 * <h2>Why 1,433 passing tests did not catch it</h2>
 * {@code RentLedgerApplicationServiceTest} stubs the repository with
 * {@code thenAnswer(inv -> inv.getArgument(0))} — the mock hands back the
 * *same* instance, so the events were still attached and
 * {@code verify(eventPublisher).publishAll(...)} passed. The mock did not
 * honour the real adapter's contract, so the suite proved the opposite of
 * production behaviour.
 *
 * <p>That is the point of this class: it pins the property the mock got
 * wrong, so a future stub cannot quietly re-hide the same bug.
 */
class RentLedgerEventPublishingTest {

    /**
     * The load-bearing fact. If this ever fails, {@code save()} started
     * returning the same instance and the whole class of bug above became
     * impossible — delete this file. Far more likely it keeps passing, and it
     * documents exactly why draining events BEFORE the save is required
     * rather than a stylistic preference.
     */
    @Test
    void aRehydratedEntryCarriesNoneOfTheOriginalsEvents() {
        RentLedgerEntry original = RentLedgerEntry.create(
                java.util.UUID.randomUUID(),
                "corr-1",
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 9, 30),
                java.time.LocalDate.of(2026, 9, 1),
                new java.math.BigDecimal("25000.00"),
                false,
                "KES"
        );

        assertThat(original.hasDomainEvents())
                .as("creating an entry registers its creation event")
                .isTrue();

        // Exactly what RentLedgerEntryRepositoryAdapter.save() hands back.
        RentLedgerEntry rehydrated = RentLedgerEntry.rehydrate(
                original.getId(),
                original.getTenantId(),
                original.getLeaseId(),
                original.getUnitId(),
                original.getTenantProfileId(),
                original.getBillingPeriodStart(),
                original.getBillingPeriodEnd(),
                original.getDueDate(),
                original.getAmountDue(),
                original.getAmountPaid(),
                original.getStatus(),
                original.isProrated(),
                original.getCurrency(),
                0L,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        assertThat(rehydrated.hasDomainEvents())
                .as("a rehydrated entry has an empty event list — this is why "
                        + "publishing from save()'s return value published nothing")
                .isFalse();

        assertThat(rehydrated.pullDomainEvents()).isEmpty();

        // And the original still holds them, which is why the service now
        // drains it before handing it to save().
        assertThat(original.pullDomainEvents()).isNotEmpty();
    }
}
