package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.sms.PhoneMasker;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderPolicyRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides which rent reminders are owed on a given day and hands each one to
 * {@link RentReminderRecorder} to be recorded and enqueued.
 *
 * <h2>Reminders are a projection over the ledger, never a parallel model</h2>
 * Everything this class needs already exists on {@link RentLedgerEntry}: what
 * is owed, when it was due, and whether it is settled. Nothing here writes to
 * the ledger, and nothing here holds a second copy of a balance. That
 * separation is the whole design — escalation is a communications decision,
 * and if it lived in ledger status then a failed SMS could change what
 * somebody owes.
 *
 * <h2>One query, not one per milestone</h2>
 * A single bounded fetch covers every due date any milestone could care about
 * on this run (today+7 back to today−7), and each entry is then matched to a
 * milestone in memory. Six separate queries would be six table scans for the
 * same rows.
 *
 * <h2>Balance owed, not rent amount</h2>
 * Every message quotes {@code entry.getBalanceOwed()}. The old overdue
 * notification path quoted {@code lease.getRentAmount()} instead, so a renter
 * who had paid nine tenths of the month was told they owed the whole of it —
 * a message that invites either an overpayment or an argument.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentReminderService {

    /** Only entries that still owe something can be reminded about. */
    private static final List<RentLedgerStatus> OUTSTANDING = List.of(
            RentLedgerStatus.DUE,
            RentLedgerStatus.PARTIALLY_PAID,
            RentLedgerStatus.OVERDUE
    );

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentReminderPolicyRepository policyRepository;
    private final RentReminderRecorder recorder;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;

    /** What one sweep did, for the scheduler to log. */
    public record SweepResult(int candidates, int sent, int alreadySent, int skipped, int failed) {}

    /**
     * Runs the reminder cadence for {@code runDate}.
     *
     * <p>Never throws: a sweep that aborts halfway leaves some tenants
     * reminded and others not, with no record of where it stopped. Each entry
     * is isolated, and failures are counted and logged rather than propagated.
     */
    public SweepResult sweep(LocalDate runDate) {
        LocalDate from = ReminderMilestone.earliestDueDateOfInterest(runDate);
        LocalDate to = ReminderMilestone.latestDueDateOfInterest(runDate);

        List<RentLedgerEntry> candidates =
                rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(OUTSTANDING, from, to);

        // Policies are read once per landlord per sweep, not once per entry.
        // A landlord with two hundred units would otherwise trigger two
        // hundred identical policy queries.
        Map<UUID, Map<ReminderMilestone, RentReminderPolicy>> policyCache = new HashMap<>();

        int sent = 0, alreadySent = 0, skipped = 0, failed = 0;

        for (RentLedgerEntry entry : candidates) {
            try {
                Outcome outcome = processEntry(entry, runDate, policyCache);
                sent += outcome.sent();
                alreadySent += outcome.alreadySent();
                if (outcome.sent() == 0 && outcome.alreadySent() == 0) {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Rent reminder failed for ledger entry id={} dueDate={}",
                        entry.getId(), entry.getDueDate(), e);
            }
        }

        return new SweepResult(candidates.size(), sent, alreadySent, skipped, failed);
    }

    private record Outcome(int sent, int alreadySent) {}

    private Outcome processEntry(
            RentLedgerEntry entry,
            LocalDate runDate,
            Map<UUID, Map<ReminderMilestone, RentReminderPolicy>> policyCache
    ) {
        Optional<ReminderMilestone> milestoneOpt =
                ReminderMilestone.at(runDate, entry.getDueDate());
        if (milestoneOpt.isEmpty()) {
            // Most days sit between milestones. Saying nothing is the
            // correct behaviour, not a gap.
            return new Outcome(0, 0);
        }
        ReminderMilestone milestone = milestoneOpt.get();

        if (entry.getBalanceOwed() == null || entry.getBalanceOwed().signum() <= 0) {
            return new Outcome(0, 0);
        }

        UUID tenantId = entry.getTenantId();
        RentReminderPolicy policy = policyCache
                .computeIfAbsent(tenantId, this::loadPolicies)
                .get(milestone);

        // NOTE: there is deliberately no account-wide channel switch consulted
        // here. tenant_settings.smsNotificationsEnabled looks like one, but
        // com.rentmanager.modules.tenant.domain.model.TenantSettings is an
        // @Entity mapped to a "tenant_settings" table that no migration
        // creates, and ddl-auto is none — so the table does not exist and the
        // flag has nowhere to persist. Reading it would either fail at runtime
        // or, worse, silently return false and mute every reminder.
        //
        // rent_reminder_policies is the real control: it is migrated, it is
        // per-milestone, and switching SMS off across all six rows is strictly
        // more expressive than one global toggle would be.
        List<NotificationChannel> renterChannels = policy.renterChannels();
        List<NotificationChannel> landlordChannels = policy.landlordChannels();
        if (renterChannels.isEmpty() && landlordChannels.isEmpty()) {
            return new Outcome(0, 0);
        }

        Lease lease = leaseRepository.findByIdAndTenantId(entry.getLeaseId(), tenantId).orElse(null);
        if (lease == null) {
            return new Outcome(0, 0);
        }

        TenantProfile renter = tenantProfileRepository.findById(lease.getTenantProfileId()).orElse(null);
        if (renter == null) {
            return new Outcome(0, 0);
        }

        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId).orElse(null);
        String unitNumber = unit == null ? null : unit.getUnitNumber();
        String currency = entry.getCurrency();
        Instant now = Instant.now();

        int sent = 0, alreadySent = 0;

        for (NotificationChannel channel : renterChannels) {
            String recipient = addressFor(channel, renter.getPhone(), renter.getEmail());
            if (recipient == null) {
                continue;
            }
            ReminderMessageFactory.Message message = ReminderMessageFactory.forRenter(
                    milestone, renter.getFullName(), unitNumber,
                    entry.getBalanceOwed(), currency, entry.getDueDate());

            Dispatched result = dispatch(entry, lease, milestone, ReminderAudience.RENTER,
                    channel, recipient, message, currency, now);
            sent += result.sent() ? 1 : 0;
            alreadySent += result.duplicate() ? 1 : 0;
        }

        if (!landlordChannels.isEmpty()) {
            Tenant landlord = tenantRepository.findById(tenantId).orElse(null);
            if (landlord != null) {
                for (NotificationChannel channel : landlordChannels) {
                    String recipient = addressFor(channel, landlord.getPhoneNumber(), landlord.getEmail());
                    if (recipient == null) {
                        continue;
                    }
                    ReminderMessageFactory.Message message = ReminderMessageFactory.forLandlord(
                            milestone, renter.getFullName(), unitNumber,
                            entry.getBalanceOwed(), currency, entry.getDueDate());

                    Dispatched result = dispatch(entry, lease, milestone, ReminderAudience.LANDLORD,
                            channel, recipient, message, currency, now);
                    sent += result.sent() ? 1 : 0;
                    alreadySent += result.duplicate() ? 1 : 0;
                }
            }
        }

        return new Outcome(sent, alreadySent);
    }

    private record Dispatched(boolean sent, boolean duplicate) {}

    private Dispatched dispatch(
            RentLedgerEntry entry,
            Lease lease,
            ReminderMilestone milestone,
            ReminderAudience audience,
            NotificationChannel channel,
            String recipient,
            ReminderMessageFactory.Message message,
            String currency,
            Instant now
    ) {
        String masked = maskFor(channel, recipient);
        try {
            recorder.recordAndEnqueue(
                    entry.getTenantId(), entry.getId(), lease.getId(),
                    milestone, audience, channel,
                    recipient, masked,
                    message.subject(), message.body(),
                    entry.getBalanceOwed(), currency, entry.getDueDate(), now);

            log.info("Rent reminder queued. entryId={} milestone={} audience={} channel={} recipient={}",
                    entry.getId(), milestone, audience, channel, masked);
            return new Dispatched(true, false);

        } catch (DataIntegrityViolationException e) {
            // The unique index did its job: this exact reminder already went
            // out. Expected on any re-run, and not an error.
            log.debug("Rent reminder already sent, skipping. entryId={} milestone={} audience={} channel={}",
                    entry.getId(), milestone, audience, channel);
            return new Dispatched(false, true);
        }
    }

    /**
     * Stored preferences for a landlord, backfilled with defaults for any
     * milestone they have not configured. A landlord created after
     * {@code V85} ran has no rows at all and must still be reminded on the
     * default cadence.
     */
    private Map<ReminderMilestone, RentReminderPolicy> loadPolicies(UUID tenantId) {
        Map<ReminderMilestone, RentReminderPolicy> byMilestone =
                new EnumMap<>(ReminderMilestone.class);
        for (RentReminderPolicy stored : policyRepository.findByTenant(tenantId)) {
            byMilestone.put(stored.getMilestone(), stored);
        }
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            byMilestone.computeIfAbsent(
                    milestone, m -> RentReminderPolicy.defaultFor(tenantId, m));
        }
        return byMilestone;
    }

    /** The address this channel delivers to, or null when it is missing. */
    private static String addressFor(NotificationChannel channel, String phone, String email) {
        String value = switch (channel) {
            case SMS, WHATSAPP -> phone;
            case EMAIL -> email;
            // Reminder policies do not offer push; a push address is a device
            // token resolved per person, not a field on the renter profile.
            case PUSH -> null;
        };
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Masks a recipient for logging. Phone numbers go through the shared
     * {@code PhoneMasker}; email keeps the domain, which is what makes a log
     * line diagnosable, and hides the local part, which is what identifies a
     * person.
     */
    private static String maskFor(NotificationChannel channel, String recipient) {
        if (channel == NotificationChannel.EMAIL) {
            int at = recipient.indexOf('@');
            if (at <= 0) {
                return "****";
            }
            String local = recipient.substring(0, at);
            String visible = local.substring(0, 1).toLowerCase(Locale.ROOT);
            return visible + "***" + recipient.substring(at);
        }
        return PhoneMasker.mask(recipient);
    }
}
