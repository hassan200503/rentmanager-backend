package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentReminder;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The transactional unit of one reminder: record that we are sending it, and
 * enqueue it, atomically.
 *
 * <h2>Why this is its own bean</h2>
 * Spring's {@code @Transactional} is proxy-based, so a call from another
 * method of the same class bypasses it entirely and runs with no transaction
 * at all. {@code RentReminderScheduler} already had that bug — a
 * {@code @Transactional} method invoked as {@code this.sendReminderForEntry(...)}
 * from the sweep in the same class, silently doing nothing. Splitting the
 * per-item transaction into a separate bean is the fix and matches how
 * {@code RentPaymentCallbackService} and {@code DisbursementRetryScheduler}
 * are already structured here.
 *
 * <h2>Why REQUIRES_NEW</h2>
 * A duplicate reminder raises a constraint violation that poisons its
 * transaction. Running each reminder in its own transaction means that
 * rollback discards exactly one reminder rather than aborting the whole
 * morning's sweep — with hundreds of tenants, one already-sent message must
 * not stop the other four hundred.
 */
@Service
@RequiredArgsConstructor
public class RentReminderRecorder {

    private final RentReminderRepository reminderRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    /**
     * Records and enqueues one reminder.
     *
     * <p>Ordering is deliberate: the reminder row is written and flushed
     * <em>before</em> the outbox row. If this reminder has already been sent,
     * the unique index rejects it here, the transaction rolls back, and no
     * delivery is ever enqueued. Writing the delivery first would leave a
     * message queued for sending on the way to discovering it was a
     * duplicate.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when
     *         this exact reminder was already sent. The caller treats that as
     *         "already done", not as a failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAndEnqueue(
            UUID tenantId,
            UUID rentLedgerEntryId,
            UUID leaseId,
            ReminderMilestone milestone,
            ReminderAudience audience,
            NotificationChannel channel,
            String recipient,
            String recipientMasked,
            String subject,
            String body,
            BigDecimal balanceOwed,
            String currency,
            LocalDate dueDate,
            Instant sentAt
    ) {
        // Built in memory first so its id can be referenced by the reminder
        // record; nothing is persisted until the reminder insert succeeds.
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId,
                rentLedgerEntryId,
                channel,
                recipient,
                subject,
                body,
                null
        );

        RentReminder reminder = RentReminder.record(
                tenantId,
                rentLedgerEntryId,
                leaseId,
                milestone,
                audience,
                channel,
                recipientMasked,
                balanceOwed,
                currency,
                dueDate,
                delivery.getId(),
                sentAt
        );

        reminderRepository.save(reminder);
        deliveryRepository.save(delivery);
    }
}
