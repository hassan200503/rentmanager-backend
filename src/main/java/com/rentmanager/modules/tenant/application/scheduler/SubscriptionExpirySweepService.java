package com.rentmanager.modules.tenant.application.scheduler;

import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.daraja.SubscriptionPaymentCallbackTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Transactional per-item work for {@link SubscriptionExpiryScheduler}
 * (each method runs in its own transaction via Spring's proxy, so a
 * failure in one tenant never rolls back the sweep's other items).
 *
 * <p>State-transition ONLY - this sweep NEVER initiates a payment. Since
 * the M-Pesa Ratiba standing-order model, collections arrive via the C2B
 * confirmation callback (each matched payment extends the period); the
 * scheduler only advances subscriptions whose paid period has ended with
 * no such payment:</p>
 *
 * <ul>
 *   <li>Period ended, auto-renew on, no payment received -&gt;
 *       GRACE_PERIOD (premium benefits continue; grace window anchored at
 *       the original period end so a late Ratiba payment still covers it).</li>
 *   <li>GRACE_PERIOD ended unpaid -&gt; revert to COMMISSION (LAPSED).
 *       Never locks the landlord out of core product function.</li>
 *   <li>Non-renewal revert: ACTIVE + auto-renew off + period ended
 *       (voluntary downgrade) -&gt; revert to COMMISSION, no clawback.</li>
 *   <li>Stale Pay-Now requests: PENDING older than the expiry window with
 *       no callback -&gt; EXPIRED; a RENEWAL request then enters the grace
 *       window (same path as a failure callback).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpirySweepService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPaymentRequestRepository paymentRequestRepository;
    private final SubscriptionPaymentCallbackTransactionService callbackTxService;
    private final PlatformSettingsService platformSettingsService;

    public void enterGraceForExpiredSubscriptions() {
        LocalDate today = LocalDate.now();
        int graceDays = platformSettingsService.getEffectiveSettings().getPremiumGraceDays();
        List<Tenant> due = tenantRepository.findPremiumRenewalsDue(today);
        if (due.isEmpty()) {
            return;
        }
        log.info("Subscription expiry sweep found {} candidate(s) with period ended on or before {} (graceDays={})",
                due.size(), today, graceDays);
        for (Tenant tenant : due) {
            try {
                graceOne(tenant.getId(), graceDays);
            } catch (Exception e) {
                log.error("Failed to move subscription into grace - will retry on next sweep. tenantId={}",
                        tenant.getId(), e);
            }
        }
    }

    public void revertNonRenewingSubscriptions() {
        LocalDate today = LocalDate.now();
        List<Tenant> due = tenantRepository.findPremiumNonRenewalsDue(today);
        if (due.isEmpty()) {
            return;
        }
        log.info("Subscription non-renewal sweep found {} candidate(s) with period ended on or before {}",
                due.size(), today);
        for (Tenant tenant : due) {
            try {
                revertOne(tenant.getId());
            } catch (Exception e) {
                log.error("Failed to revert non-renewing subscription - will retry on next sweep. tenantId={}",
                        tenant.getId(), e);
            }
        }
    }

    public void revertOverdueGraceSubscriptions() {
        LocalDate today = LocalDate.now();
        List<Tenant> overdue = tenantRepository.findPremiumGraceOverdue(today);
        if (overdue.isEmpty()) {
            return;
        }
        log.info("Subscription grace sweep found {} candidate(s) with grace ended on or before {}",
                overdue.size(), today);
        for (Tenant tenant : overdue) {
            try {
                revertOne(tenant.getId());
            } catch (Exception e) {
                log.error("Failed to revert overdue-grace subscription - will retry on next sweep. tenantId={}",
                        tenant.getId(), e);
            }
        }
    }

    public void expireStalePaymentRequests() {
        int expiryMinutes = platformSettingsService.getEffectiveSettings()
                .getSubscriptionPaymentExpiryMinutes();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(expiryMinutes));
        List<SubscriptionPaymentRequest> stale = paymentRequestRepository
                .findByStatusAndCreatedAtBefore(SubscriptionPaymentRequestStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Stale subscription payment sweep found {} candidate(s) older than {} (expiryMinutes={})",
                stale.size(), cutoff, expiryMinutes);
        for (SubscriptionPaymentRequest request : stale) {
            try {
                expireOne(request.getId());
            } catch (Exception e) {
                log.error("Failed to expire stale subscription payment - will retry on next sweep. requestId={}",
                        request.getId(), e);
            }
        }
    }

    /**
     * Period ended with no matching Ratiba/C2B payment - enter the grace
     * window. Grace is anchored at the original period end (not "today"),
     * so a late standing-order execution still lands inside the window and
     * extends the period from its anchor, covering the overdue cycle.
     */
    @Transactional
    public void graceOne(UUID tenantId, int graceDays) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !tenant.isPremiumBilling()
                || tenant.getSubscriptionStatus() != SubscriptionStatus.ACTIVE
                || !tenant.isPlanAutoRenew()
                || tenant.getPlanEndDate() == null
                || tenant.getPlanEndDate().isAfter(LocalDate.now())) {
            log.debug("Grace candidate no longer eligible, skipping. tenantId={}", tenantId);
            return;
        }

        tenant.enterPremiumGracePeriod(tenant.getPlanEndDate().plusDays(graceDays));
        tenantRepository.save(tenant);
        log.warn("Premium subscription entered grace window. tenantId={} planEndDate={} graceEndsAt={} graceDays={}",
                tenantId, tenant.getPlanEndDate(), tenant.getPlanGraceEndsAt(), graceDays);
    }

    /**
     * Covers both revert paths: voluntary downgrade whose paid period
     * ended, and a grace window that ended unpaid.
     */
    @Transactional
    public void revertOne(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !tenant.isPremiumBilling()) {
            log.debug("Revert candidate no longer premium, skipping. tenantId={}", tenantId);
            return;
        }

        tenant.revertToCommissionBilling();
        tenantRepository.save(tenant);
        log.warn("Premium subscription reverted to COMMISSION billing. tenantId={}", tenantId);
    }

    @Transactional
    public void expireOne(UUID requestId) {
        SubscriptionPaymentRequest request = paymentRequestRepository.findById(requestId)
                .orElse(null);
        if (request == null) {
            log.debug("SubscriptionPaymentRequest already deleted or not found. requestId={}", requestId);
            return;
        }

        if (request.getStatus() != SubscriptionPaymentRequestStatus.PENDING) {
            log.info("SubscriptionPaymentRequest no longer PENDING, skipping. requestId={} currentStatus={}",
                    requestId, request.getStatus());
            return;
        }

        request.markExpired("No M-Pesa callback received within the stale window");
        paymentRequestRepository.save(request);

        log.warn("Expired stale subscription payment (no M-Pesa callback received). " +
                "requestId={} tenantId={} amount={} purpose={}",
                requestId, request.getTenantId(), request.getAmount(), request.getPurpose());

        if (request.isRenewal()) {
            callbackTxService.enterGracePeriod(request.getTenantId());
        }
    }
}
