package com.rentmanager.modules.rentledger.application.autopay;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.autopay.AutoPaySettingsRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPayService {

    private final AutoPaySettingsRepository autoPaySettingsRepository;
    private final LeaseRepository leaseRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final RentPaymentInitiationService rentPaymentInitiationService;
    private final SmsService smsService;
// -------------------------------------------------------
    // TENANT-FACING API
    // -------------------------------------------------------

    @Transactional
    public AutoPaySettings toggle(UUID tenantId, UUID leaseId, UUID tenantProfileId, boolean enable, String mpesaPhone) {
        Optional<AutoPaySettings> existing = autoPaySettingsRepository.findByLeaseIdAndTenantId(leaseId, tenantId);

        AutoPaySettings settings;
        if (existing.isPresent()) {
            settings = existing.get();
            if (enable) {
                settings.updatePhone(mpesaPhone);
                settings.enable();
            } else {
                settings.disable();
            }
        } else {
            settings = AutoPaySettings.create(tenantId, leaseId, tenantProfileId, mpesaPhone);
            if (enable) {
                settings.enable();
            }
        }

        settings = autoPaySettingsRepository.save(settings);
        log.info("Auto-pay {} for leaseId={} tenantId={}", enable ? "enabled" : "disabled", leaseId, tenantId);
        return settings;
    }

    @Transactional(readOnly = true)
    public Optional<AutoPaySettings> getSettings(UUID tenantId, UUID leaseId) {
        return autoPaySettingsRepository.findByLeaseIdAndTenantId(leaseId, tenantId);
    }

    @Transactional
    public AutoPaySettings updatePhone(UUID tenantId, UUID leaseId, String mpesaPhone) {
        AutoPaySettings settings = autoPaySettingsRepository.findByLeaseIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Auto-pay settings not found for lease: " + leaseId));

        settings.updatePhone(mpesaPhone);
        settings = autoPaySettingsRepository.save(settings);
        log.info("Auto-pay phone updated for leaseId={}", leaseId);
        return settings;
    }

    // -------------------------------------------------------
    // SCHEDULER API
    // -------------------------------------------------------

    @Transactional
    public void processAll() {
        List<AutoPaySettings> allEnabled = autoPaySettingsRepository.findAllByEnabledTrue();
        log.info("AutoPayScheduler: processing {} enabled auto-pay settings", allEnabled.size());

        for (AutoPaySettings settings : allEnabled) {
            try {
                processOne(settings);
            } catch (Exception e) {
                log.error("AutoPayScheduler: failed to process leaseId={}", settings.getLeaseId(), e);
            }
        }
    }

    private void processOne(AutoPaySettings settings) {
        UUID tenantId = settings.getTenantId();
        UUID leaseId = settings.getLeaseId();

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(tenantId, leaseId);
        Optional<RentLedgerEntry> dueEntry = entries.stream()
                .filter(e -> e.getStatus().isOutstanding() && e.getBalanceOwed().signum() > 0)
                .findFirst();

        if (dueEntry.isEmpty()) {
            log.debug("AutoPayScheduler: no outstanding entry for leaseId={}", leaseId);
            return;
        }

        RentLedgerEntry entry = dueEntry.get();

        if (!"DUE".equals(entry.getStatus().name())) {
            log.debug("AutoPayScheduler: entry {} is {}, not DUE — skipping auto-pay for leaseId={}",
                    entry.getId(), entry.getStatus(), leaseId);
            return;
        }

        String phone = settings.getMpesaPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("AutoPayScheduler: no mpesaPhone set for leaseId={} — disabling auto-pay", leaseId);
            settings.disable();
            autoPaySettingsRepository.save(settings);
            return;
        }

        try {
            rentPaymentInitiationService.initiate(tenantId, entry.getId(), phone);
            settings.recordSuccess();
            log.info("AutoPayScheduler: auto-pay initiated for leaseId={} entryId={} amount={}",
                    leaseId, entry.getId(), entry.getBalanceOwed());

            TenantProfile profile = tenantProfileRepository.findById(settings.getTenantProfileId()).orElse(null);
            if (profile != null && profile.getPhone() != null && !profile.getPhone().isBlank()) {
                smsService.sendAutoPayConfirmation(profile.getPhone(),
                        entry.getBalanceOwed().toString(), phone);
            }
        } catch (Exception e) {
            settings.recordFailure();
            log.warn("AutoPayScheduler: auto-pay failed for leaseId={} attempt={}/3",
                    leaseId, settings.getConsecutiveFailures());

            if (settings.getConsecutiveFailures() == 1) {
                TenantProfile profile = tenantProfileRepository.findById(settings.getTenantProfileId()).orElse(null);
                if (profile != null && profile.getPhone() != null && !profile.getPhone().isBlank()) {
                    smsService.sendAutoPayFailed(profile.getPhone(), "check your M-Pesa phone and try again");
                }
            }

            if (!settings.shouldRetry()) {
                TenantProfile profile = tenantProfileRepository.findById(settings.getTenantProfileId()).orElse(null);
                if (profile != null && profile.getPhone() != null && !profile.getPhone().isBlank()) {
                    smsService.sendAutoPayFailed(profile.getPhone(),
                            "auto-pay disabled after 3 failed attempts. Re-enable in your portal.");
                }
            }
        }

        autoPaySettingsRepository.save(settings);
    }
}