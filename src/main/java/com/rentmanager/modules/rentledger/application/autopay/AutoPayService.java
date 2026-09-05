package com.rentmanager.modules.rentledger.application.autopay;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.autopay.AutoPaySettingsRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

        // FIX (2026-09-03): two defects lived in this lookup.
        //
        // 1. It skipped anything that was not exactly DUE. RentOverdueScheduler
        //    flips entries to OVERDUE daily once dueDate + gracePeriodDays
        //    passes, so a renter whose auto-pay failed through the grace
        //    window crossed into OVERDUE and auto-pay never touched that
        //    entry again — silently, while the portal still showed the
        //    toggle as enabled. Auto-pay now covers the whole outstanding
        //    set: a late month is still that renter's rent, and paying it is
        //    the thing they switched this on for.
        //
        // 2. findFirst() over an unordered repository result meant that a
        //    renter two months behind had whichever row the database
        //    happened to return first paid. Arrears are now cleared oldest
        //    first, which is both what a renter expects and what stops the
        //    oldest debt ageing further.
        Optional<RentLedgerEntry> dueEntry = rentLedgerEntryRepository.findByLease(tenantId, leaseId)
                .stream()
                .filter(e -> e.getStatus().isOutstanding() && e.getBalanceOwed().signum() > 0)
                .min(Comparator.comparing(RentLedgerEntry::getDueDate));

        if (dueEntry.isEmpty()) {
            log.debug("AutoPayScheduler: no outstanding entry for leaseId={}", leaseId);
            return;
        }

        RentLedgerEntry entry = dueEntry.get();

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
            boolean willDisable = settings.getConsecutiveFailures() + 1 >= 3;
            settings.recordFailure(classifyFailure(e, willDisable));
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

    /**
     * Maps a caught exception (+ whether this failure crosses the 3-strike
     * auto-disable threshold) to a short, renter-safe explanation for
     * AutoPaySettings.lastFailureReason. Deliberately NEVER returns the raw
     * exception message: DarajaException/provider error text can embed a
     * phone number or other request detail, and this codebase's rule
     * against logging full phone numbers extends, if anything, more
     * strictly to a field a renter will see rendered in their own portal.
     * A small fixed set of categories is both safer and more useful to a
     * renter than a raw stack-trace string would be. Framing also matters:
     * "we'll retry" would be actively misleading on the failure that just
     * auto-disabled the feature.
     */
    private String classifyFailure(Exception e, boolean willDisable) {
        String cause = (e instanceof DarajaException)
                ? "M-Pesa couldn't process the request."
                : "Auto-pay hit a temporary system issue.";
        return willDisable
                ? cause + " Auto-pay has been turned off after 3 failed attempts — re-enable it once the issue is resolved."
                : cause + " We'll retry automatically.";
    }
}