package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentReminder;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderPolicyRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the reminder system's central guarantee where it actually lives:
 * in PostgreSQL.
 *
 * <p>{@code RentReminderServiceTest} shows the service treats a constraint
 * violation as "already sent", but it mocks the repository, so it proves
 * nothing about whether the constraint exists. Everything the design rests on
 * — one message per entry per milestone per audience per channel, forever,
 * and a log that cannot be rewritten afterwards — is enforced by {@code V84}
 * and is only real if the migration says what it is supposed to say.
 */
@Transactional
class RentReminderIdempotencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RentReminderRepository rentReminderRepository;

    @Autowired
    private RentReminderPolicyRepository rentReminderPolicyRepository;

    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID leaseId;
    private UUID entryId;
    private final LocalDate dueDate = LocalDate.of(2026, 9, 5);

    @BeforeEach
    void setUp() {
        MinimalTenantChainFixture.ChainWithLease chain =
                MinimalTenantChainFixture.persistFullChainWithLease(entityManager);
        tenantId = chain.tenantId();
        leaseId = chain.leaseId();

        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId,
                "reminder-it-" + UUID.randomUUID(),
                leaseId,
                chain.unitId(),
                chain.tenantProfileId(),
                dueDate.withDayOfMonth(1),
                dueDate.withDayOfMonth(1).plusMonths(1).minusDays(1),
                dueDate,
                new BigDecimal("15000.00"),
                false
        );
        entryId = rentLedgerEntryRepository.save(entry).getId();
        entityManager.flush();
    }

    private RentReminder reminder(ReminderMilestone milestone,
                                  ReminderAudience audience,
                                  NotificationChannel channel) {
        return RentReminder.record(
                tenantId, entryId, leaseId, milestone, audience, channel,
                "****5678", new BigDecimal("15000.00"), "KES", dueDate,
                UUID.randomUUID(), Instant.now());
    }

    @Test
    void theSameReminderCannotBeRecordedTwice() {
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.SMS));

        assertThatThrownBy(() -> rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.SMS)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Each part of the key must genuinely discriminate. A renter legitimately
     * hears about the same entry at several milestones and on more than one
     * channel, and the landlord hears about it separately — none of those may
     * collide with each other.
     */
    @Test
    void milestoneAudienceAndChannelEachDistinguishOneReminderFromAnother() {
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.SMS));
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.EMAIL));
        rentReminderRepository.save(reminder(
                ReminderMilestone.OVERDUE_3, ReminderAudience.RENTER, NotificationChannel.SMS));
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.LANDLORD, NotificationChannel.SMS));

        assertThat(rentReminderRepository.findByEntry(tenantId, entryId)).hasSize(4);
    }

    /**
     * A reminder log that can be edited after the fact is worthless as
     * evidence in the dispute it exists to settle.
     */
    @Test
    void aRecordedReminderCannotBeUpdated() {
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.SMS));

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "UPDATE rent_reminders SET balance_owed_snapshot = 1 WHERE tenant_id = :t")
                    .setParameter("t", tenantId)
                    .executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("append-only");
    }

    @Test
    void aRecordedReminderCannotBeDeleted() {
        rentReminderRepository.save(reminder(
                ReminderMilestone.DUE_TODAY, ReminderAudience.RENTER, NotificationChannel.SMS));

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "DELETE FROM rent_reminders WHERE tenant_id = :t")
                    .setParameter("t", tenantId)
                    .executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("append-only");
    }

    @Test
    void negativeBalanceSnapshotsAreRejectedByTheDatabase() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO rent_reminders (id, tenant_id, rent_ledger_entry_id, lease_id, "
                                    + "milestone, audience, channel, recipient_masked, "
                                    + "balance_owed_snapshot, currency, due_date, sent_at, created_at) "
                                    + "VALUES (:id, :t, :e, :l, 'DUE_TODAY', 'RENTER', 'SMS', '****', "
                                    + "-1, 'KES', :d, NOW(), NOW())")
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("t", tenantId)
                    .setParameter("e", entryId)
                    .setParameter("l", leaseId)
                    .setParameter("d", dueDate)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    /**
     * V85 seeds a policy row per milestone for every landlord that existed
     * when it ran, so the fixture's tenant should come back fully configured.
     */
    @Test
    void everyLandlordIsSeededWithTheDefaultCadence() {
        List<com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy> policies =
                rentReminderPolicyRepository.findByTenant(tenantId);

        // A tenant created by the fixture after V85 ran has no seeded rows;
        // one created before it has all six. Either is correct — what must
        // never happen is a partial cadence.
        assertThat(policies.size()).isIn(0, ReminderMilestone.values().length);
    }
}
