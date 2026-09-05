package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.RentReminder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port for the append-only rent reminder log.
 *
 * <p>There is no {@code update} and no {@code delete} on purpose: the table's
 * trigger ({@code V84}) rejects both, so offering them here would only let a
 * caller discover that at runtime.
 */
public interface RentReminderRepository {

    /**
     * Persists a reminder record, flushing immediately so that a duplicate
     * surfaces here rather than at commit.
     *
     * <p><strong>Throws on duplicate, and that is the contract.</strong> When
     * the unique index
     * {@code (tenant_id, rent_ledger_entry_id, milestone, audience, channel)}
     * already holds a matching row, this raises
     * {@code DataIntegrityViolationException}.
     *
     * <p>It would be friendlier to return {@code false}, and it would be
     * wrong. The caller inserts the outbox delivery and this record in one
     * transaction, so that recording and sending succeed or fail together —
     * a swallowed duplicate would leave that transaction alive and enqueue a
     * second message. Letting the exception escape rolls the whole unit back,
     * which is precisely the desired outcome: no row, no message.
     *
     * <p>The caller is expected to run each reminder in its own transaction
     * ({@code REQUIRES_NEW}) so one duplicate does not abort the sweep, and
     * to treat the exception as "already sent" rather than as an error.
     * Insert-and-catch is deliberate in preference to check-then-insert:
     * two schedulers racing across a redeploy would both read "not sent" and
     * both send. The database arbitrates instead — the same reasoning as the
     * M-Pesa replay guard in {@code V33}.
     */
    void save(RentReminder reminder);

    /** Every reminder already sent for one ledger entry, tenant-scoped. */
    List<RentReminder> findByEntry(UUID tenantId, UUID rentLedgerEntryId);

    /**
     * Reminder history for a landlord across a due-date window — the query
     * behind "what did we send, and what did the SMS cost me".
     */
    List<RentReminder> findByTenantAndDueDateBetween(
            UUID tenantId, LocalDate from, LocalDate to);
}
