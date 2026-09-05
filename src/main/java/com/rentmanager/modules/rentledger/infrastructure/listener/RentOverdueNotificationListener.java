package com.rentmanager.modules.rentledger.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.events.RentOverdueDetected;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.rentmanager.shared.util.money.MoneyFormatter;

import java.util.UUID;

/**
 * Tells the <strong>landlord</strong> the moment a rent ledger entry crosses
 * its grace period. Fires once, on the {@code RentOverdueDetected} event
 * raised by the 02:30 overdue sweep.
 *
 * <h2>Why this no longer messages the renter</h2>
 * It used to send the renter an SMS here as well, and that became a double
 * notification the day {@code RentReminderService} took over the reminder
 * cadence. {@code Lease.gracePeriodDays} is nullable and
 * {@code RentOverdueScheduler} treats null as zero, so for any lease without
 * an explicit grace period this event fires on day +1 — precisely where the
 * cadence's {@code OVERDUE_1} milestone sits. The renter would have been
 * contacted twice about one event, by two paths, with only one of them
 * recorded in {@code rent_reminders}.
 *
 * <p>The renter is now reminded exclusively by the cadence, which is
 * deduplicated at the database, records what it said, and escalates through
 * day +1, +3 and +7 rather than firing once. That is strictly more contact
 * than this listener provided, not less.
 *
 * <p>The landlord leg stays here because it answers a different question.
 * This says "a tenancy just went past its grace period"; the cadence's
 * {@code OVERDUE_7} says "a week has passed and it is still unpaid". Both are
 * worth knowing, and they are far enough apart not to read as repetition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentOverdueNotificationListener {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRentOverdueDetected(RentOverdueDetected event) {
        try {
            UUID tenantId = event.getTenantId();
            UUID leaseId = event.getLeaseId();
            UUID tenantProfileId = event.getTenantProfileId();
            String daysOverdue = String.valueOf(event.getDaysOverdue());

            Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

            TenantProfile renterProfile = tenantProfileRepository.findById(tenantProfileId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found: " + tenantProfileId));

            Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + lease.getUnitId()));

            Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Property not found: " + unit.getPropertyId()));

            Tenant landlord = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Landlord not found: " + tenantId));

            // The balance still owed, NOT the full monthly rent. This used to
            // read lease.getRentAmount(), so a renter who had already paid
            // nine tenths of the month was told they owed all of it — which
            // invites either an overpayment or an argument, and makes every
            // later message less believable. RentLedgerEntry is the only
            // thing that knows what is actually outstanding.
            RentLedgerEntry entry = rentLedgerEntryRepository
                    .findByIdAndTenantId(event.getLedgerEntryId(), tenantId)
                    .orElse(null);
            if (entry == null || entry.getBalanceOwed() == null
                    || entry.getBalanceOwed().signum() <= 0) {
                return;
            }

            String formattedAmount =
                    MoneyFormatter.format(entry.getBalanceOwed(), entry.getCurrency());

            if (landlord.getPhoneNumber() != null && !landlord.getPhoneNumber().isBlank()) {
                smsService.sendRentOverdueNotificationToLandlord(
                        landlord.getPhoneNumber(),
                        renterProfile.getFullName(),
                        formattedAmount,
                        unit.getUnitNumber(),
                        daysOverdue
                );
            }
        } catch (Exception ex) {
            log.error("Failed to send overdue notifications for event: {}", event.getEventId(), ex);
        }
    }
}
