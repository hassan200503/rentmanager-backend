package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentReminderScheduler {

    private static final ZoneId TZ = ZoneId.of("Africa/Nairobi");
    private static final int REMINDER_DAYS_BEFORE_DUE = 3;

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final SmsService smsService;

    @Scheduled(cron = "0 0 9 * * *")
    public void sendUpcomingPaymentReminders() {
        LocalDate reminderDate = LocalDate.now(TZ).plusDays(REMINDER_DAYS_BEFORE_DUE);

        List<RentLedgerEntry> candidates = rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE),
                reminderDate
        );

        log.info("RentReminderScheduler: {} candidate(s) for upcoming payment reminders", candidates.size());

        for (RentLedgerEntry entry : candidates) {
            if (!entry.getDueDate().equals(reminderDate)) continue;
            try {
                sendReminderForEntry(entry);
            } catch (Exception e) {
                log.error("Failed to send reminder for entry id={}", entry.getId(), e);
            }
        }
    }

    @Transactional
    public void sendReminderForEntry(RentLedgerEntry entry) {
        UUID tenantId = entry.getTenantId();
        UUID leaseId = entry.getLeaseId();

        if (entry.getBalanceOwed().signum() <= 0) return;

        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElse(null);
        if (lease == null) return;

        TenantProfile renterProfile = tenantProfileRepository.findById(lease.getTenantProfileId())
                .orElse(null);
        if (renterProfile == null || renterProfile.getPhone() == null || renterProfile.getPhone().isBlank()) return;

        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId)
                .orElse(null);
        if (unit == null) return;

        String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(entry.getBalanceOwed());
        String formattedDate = entry.getDueDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));

        smsService.sendRentUpcomingPaymentReminder(
                renterProfile.getPhone(),
                formattedAmount,
                formattedDate
        );

        log.info("Sent upcoming payment reminder. entryId={} tenantPhone={}", entry.getId(), renterProfile.getPhone());
    }
}
