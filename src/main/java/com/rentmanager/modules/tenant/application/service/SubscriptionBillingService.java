package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.api.dto.response.RatibaSetupResponse;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionStatusResponse;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import com.rentmanager.modules.tenant.infrastructure.config.SubscriptionBillingProperties;
import com.rentmanager.modules.tenant.infrastructure.daraja.SubscriptionPaymentCallbackTransactionService;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 dual revenue model - landlord-facing subscription billing.
 *
 * <p>Confirmed semantics:</p>
 * <ul>
 *   <li>COMMISSION - existing mechanics untouched (regression target). The
 *       rate itself is never hardcoded here: it is read at rent-payment
 *       time from the active {@code commission_policies} row (landlord
 *       override -&gt; platform default -&gt; null = no commission).</li>
 *   <li>PREMIUM_MONTHLY - flat monthly fee per tier; rent payments carry
 *       zero commission (100% net B2C to the landlord).</li>
 *   <li>Switch COMMISSION -&gt; PREMIUM takes effect only on the FIRST
 *       SUCCESSFUL subscription payment (STK callback). Until then the
 *       landlord stays on COMMISSION; a failed payment changes nothing.</li>
 *   <li>Switch PREMIUM -&gt; COMMISSION takes effect at the end of the
 *       already-paid period (no clawback); mid-period it is expressed as
 *       "stop auto-renewing".</li>
 *   <li>Failed/timed-out renewal -&gt; GRACE_PERIOD (premium benefits
 *       continue) -&gt; auto-revert to COMMISSION by the scheduler when the
 *       grace window ends. Never locks a landlord out.</li>
 * </ul>
 *
 * <p>STK pushes use the platform's Daraja credentials (like rent
 * payments), NOT the landlord's own credentials - those are reserved for
 * the deposit flow. Billing period is {@code BillingCycle.MONTHLY}
 * (activation: today -&gt; +1 month; renewal: current end -&gt; +1 month).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentRequestRepository paymentRequestRepository;
    private final UnitRepository unitRepository;
    private final DarajaService darajaService;
    private final DarajaProperties darajaProperties;
    private final RatibaStandingOrderService ratibaStandingOrderService;
    private final SubscriptionPaymentCallbackTransactionService callbackTransactionService;
    private final SubscriptionBillingProperties subscriptionBillingProperties;

    /** Rate-limit for Daraja STK status queries per payment request. */
    private static final Duration STK_QUERY_MIN_INTERVAL = Duration.ofSeconds(15);

    private final Map<UUID, Instant> lastStkQueryAt = new ConcurrentHashMap<>();

    /**
     * Initiates the first subscription payment for a COMMISSION landlord
     * switching to PREMIUM_MONTHLY. The switch is only applied once the
     * payment succeeds (see SubscriptionPaymentCallbackTransactionService).
     */
    @Transactional
    public SubscriptionPaymentRequest switchToPremium(
            UUID tenantId,
            String planCode,
            String mpesaPhone
    ) {
        Tenant tenant = findTenant(tenantId);

        if (tenant.isPremiumBilling()) {
            throw new BusinessException(
                    "Landlord is already on premium monthly billing: " + tenantId,
                    ErrorCode.SUBSCRIPTION_ALREADY_PREMIUM
            );
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found: " + planCode,
                        ErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND
                ));

        if (!plan.isActive()) {
            throw new BusinessException(
                    "Subscription plan is not active: " + planCode,
                    ErrorCode.SUBSCRIPTION_PLAN_NOT_ACTIVE
            );
        }

        if (!plan.isSelfService()) {
            throw new BusinessException(
                    "Plan " + planCode + " is not self-service - contact sales for pricing",
                    ErrorCode.SUBSCRIPTION_PLAN_NOT_SELF_SERVICE
            );
        }

        if (plan.getMonthlyPrice() == null
                || plan.getMonthlyPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Subscription plan has no monthly price: " + planCode,
                    ErrorCode.SUBSCRIPTION_PLAN_PRICE_REQUIRED
            );
        }

        long unitCount = unitRepository.countByTenantId(tenantId);
        if (plan.getMaxUnits() != null && unitCount > plan.getMaxUnits()) {
            throw new BusinessException(
                    "Landlord has " + unitCount + " units, exceeds plan " + planCode
                            + " limit of " + plan.getMaxUnits(),
                    ErrorCode.SUBSCRIPTION_UNIT_LIMIT_EXCEEDED
            );
        }

        // A payment may still be recorded as in flight (e.g. the Daraja
        // callback never arrived and the stale sweep hasn't run yet).
        // Resolve its REAL status against Daraja before deciding anything,
        // so a retry can never double-charge a landlord whose money moved:
        //  - actually PAID          -> activate premium and return it
        //  - terminal failure       -> fall through to a fresh attempt
        //  - still in flight        -> return it for continued polling
        //    (no duplicate STK push while the landlord may still be
        //     entering their PIN)
        //  - stale, no Daraja answer -> EXPIRED, fall through to a fresh
        //    attempt (never blocks retry for up to 24h waiting on the
        //    once-daily sweep)
        SubscriptionPaymentRequest existing = paymentRequestRepository
                .findPendingByTenantIdAndPurpose(tenantId, SubscriptionPaymentPurpose.INITIAL_ACTIVATION)
                .orElse(null);
        if (existing != null) {
            if (existing.getMpesaCheckoutRequestId() != null) {
                applyDarajaStatus(existing.getMpesaCheckoutRequestId());
            }

            SubscriptionPaymentRequest refreshed = paymentRequestRepository
                    .findPendingByTenantIdAndPurpose(tenantId, SubscriptionPaymentPurpose.INITIAL_ACTIVATION)
                    .orElse(null);
            if (refreshed != null) {
                if (isStale(refreshed)) {
                    refreshed.markExpired("Timed out waiting for M-Pesa confirmation - please try again");
                    paymentRequestRepository.save(refreshed);
                    log.warn("Expired stale pending subscription payment on retry. requestId={}",
                            refreshed.getId());
                } else {
                    log.info("Subscription payment still in flight - returning existing request for polling. requestId={}",
                            refreshed.getId());
                    return refreshed;
                }
            } else {
                SubscriptionPaymentRequest resolved = paymentRequestRepository
                        .findByMpesaCheckoutRequestId(existing.getMpesaCheckoutRequestId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Subscription payment request not found: " + existing.getMpesaCheckoutRequestId(),
                                ErrorCode.SUBSCRIPTION_PAYMENT_REQUEST_NOT_FOUND
                        ));
                if (resolved.getStatus() == SubscriptionPaymentRequestStatus.PAID) {
                    log.info("Pending subscription payment resolved PAID via Daraja query - premium activated. requestId={}",
                            resolved.getId());
                    return resolved;
                }
            }
        }

        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.create(
                tenantId,
                plan.getId(),
                plan.getMonthlyPrice(),
                mpesaPhone,
                SubscriptionPaymentPurpose.INITIAL_ACTIVATION
        );
        request = paymentRequestRepository.save(request);

        String checkoutRequestId = darajaService.initiateSTKPush(
                mpesaPhone,
                plan.getMonthlyPrice(),
                plan.getCode(),
                "Subscription fee",
                platformCredentials(),
                darajaProperties.getSubscriptionBillingCallbackUrl()
        );

        request.attachCheckoutRequestId(checkoutRequestId);
        request = paymentRequestRepository.save(request);

        log.info("Subscription activation STK push initiated. tenantId={} planCode={} amount={} checkoutRequestId={}",
                tenantId, planCode, plan.getMonthlyPrice(), checkoutRequestId);

        return request;
    }

    /**
     * Voluntary downgrade. While premium: stops auto-renewal, so the
     * scheduler reverts the landlord to COMMISSION at the end of the
     * already-paid period (no clawback). If the landlord is currently in
     * the grace window, reverts immediately.
     */
    @Transactional
    public void cancelPremium(UUID tenantId) {
        Tenant tenant = findTenant(tenantId);

        if (!tenant.isPremiumBilling()) {
            throw new BusinessException(
                    "Landlord is not on premium monthly billing: " + tenantId,
                    ErrorCode.SUBSCRIPTION_NOT_PREMIUM
            );
        }

        if (tenant.getSubscriptionStatus() == SubscriptionStatus.GRACE_PERIOD) {
            tenant.revertToCommissionBilling();
            tenantRepository.save(tenant);
            log.info("Premium subscription cancelled during grace window - reverted immediately. tenantId={}", tenantId);
            return;
        }

        tenant.markPremiumNonRenewal();
        tenantRepository.save(tenant);
        log.info("Premium subscription marked for non-renewal - reverts to COMMISSION at period end. tenantId={}", tenantId);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(UUID tenantId) {
        Tenant tenant = findTenant(tenantId);

        SubscriptionPlan plan = tenant.getSubscriptionPlanId() != null
                ? subscriptionPlanRepository.findById(tenant.getSubscriptionPlanId()).orElse(null)
                : null;

        StandingOrderStatus standingOrderStatus = ratibaStandingOrderService.findLatest(tenantId)
                .map(order -> order.getStatus())
                .orElse(null);

        return new SubscriptionStatusResponse(
                tenant.getBillingMode(),
                tenant.getSubscriptionStatus(),
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                plan != null ? plan.getMonthlyPrice() : null,
                tenant.getPlanStartDate(),
                tenant.getPlanEndDate(),
                tenant.getPlanGraceEndsAt(),
                tenant.isPlanAutoRenew(),
                darajaProperties.getBusinessShortCode(),
                tenant.getTenantCode(),
                darajaProperties.isRatibaEnabled(),
                standingOrderStatus
        );
    }

    /**
     * Poll endpoint backing the switch-to-premium STK flow: returns the
     * current status of a payment request the landlord initiated. Lookup
     * is ownership-scoped (tenantId + id) so landlords cannot observe
     * other tenants' payment requests.
     *
     * <p>While a request is PENDING with a known CheckoutRequestID, the
     * backend also queries Daraja's STK status endpoint (throttled to one
     * query per request every {@value #STK_QUERY_MIN_INTERVAL} seconds)
     * so the flow resolves to a terminal state even when Safaricom never
     * delivers the callback — this is what lets the frontend show payment
     * success or failure immediately instead of waiting indefinitely.</p>
     */
    public SubscriptionPaymentRequest getPaymentRequestStatus(UUID tenantId, UUID paymentRequestId) {
        SubscriptionPaymentRequest request = paymentRequestRepository.findByIdAndTenantId(paymentRequestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription payment request not found: " + paymentRequestId,
                        ErrorCode.SUBSCRIPTION_PAYMENT_REQUEST_NOT_FOUND
                ));

        if (request.getStatus() == SubscriptionPaymentRequestStatus.PENDING
                && request.getMpesaCheckoutRequestId() != null
                && shouldQueryDaraja(request.getId())) {
            applyDarajaStatus(request.getMpesaCheckoutRequestId());
        }

        if (request.getStatus() != SubscriptionPaymentRequestStatus.PENDING) {
            lastStkQueryAt.remove(request.getId());
        }

        return paymentRequestRepository.findByIdAndTenantId(paymentRequestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription payment request not found: " + paymentRequestId,
                        ErrorCode.SUBSCRIPTION_PAYMENT_REQUEST_NOT_FOUND
                ));
    }

    /**
     * Asks Daraja for the authoritative status of the STK push and applies
     * the terminal outcome (PAID or FAILED) through the same transactional
     * path the callback uses. Non-terminal outcomes (still processing) and
     * any query failure leave the request PENDING — the caller decides what
     * to do next (keep polling, or expire as stale).
     */
    private void applyDarajaStatus(String checkoutRequestId) {
        try {
            DarajaService.StkQueryResult result =
                    darajaService.querySTKStatus(checkoutRequestId, platformCredentials());
            if (result.isSuccess()) {
                String receipt = (result.mpesaReceiptNumber() == null || result.mpesaReceiptNumber().isBlank())
                        ? "QRY-" + checkoutRequestId
                        : result.mpesaReceiptNumber();
                callbackTransactionService.processSuccessfulCallback(checkoutRequestId, receipt);
            } else if (result.isTerminal()) {
                callbackTransactionService.processFailedCallback(
                        checkoutRequestId,
                        result.resultDesc() != null ? result.resultDesc() : "Payment declined"
                );
            } else {
                log.info("STK push still processing - staying PENDING. CheckoutRequestID={} ResultCode={}",
                        checkoutRequestId, result.resultCode());
            }
        } catch (Exception ex) {
            log.warn("STK status query failed - keeping payment PENDING. CheckoutRequestID={}",
                    checkoutRequestId, ex);
        }
    }

    /** Rate-limits Daraja STK status queries to one per request every 15s. */
    private boolean shouldQueryDaraja(UUID requestId) {
        Instant now = Instant.now();
        Instant last = lastStkQueryAt.get(requestId);
        if (last != null && last.plus(STK_QUERY_MIN_INTERVAL).isAfter(now)) {
            return false;
        }
        lastStkQueryAt.put(requestId, now);
        return true;
    }

    /**
     * A PENDING request that has outlived the configured expiry window
     * (no callback, and Daraja's query gives no terminal answer) is
     * treated as stale so the landlord can retry. A missing createdAt
     * (legacy row) is treated as stale rather than blocking retries
     * forever.
     */
    private boolean isStale(SubscriptionPaymentRequest request) {
        Instant createdAt = request.getCreatedAt();
        return createdAt == null
                || createdAt.isBefore(Instant.now().minus(Duration.ofMinutes(
                        subscriptionBillingProperties.getPaymentRequestExpiryMinutes())));
    }

    /**
     * Initiates a M-Pesa Ratiba standing-order setup for the landlord
     * (merchant-initiated; the landlord confirms via the NI push on their
     * phone). Returns the pending order plus the Paybill / account
     * reference / fee needed for the manual *334# fallback.
     */
    @Transactional
    public RatibaSetupResponse setupRatibaStandingOrder(UUID tenantId) {
        SubscriptionStandingOrder order = ratibaStandingOrderService.createStandingOrder(tenantId);
        return RatibaSetupResponse.from(
                order,
                darajaProperties.getBusinessShortCode(),
                "Open *334# or the M-Pesa app > My Subscriptions and set up a recurring payment of "
                        + order.getAmount() + " KES to Paybill " + darajaProperties.getBusinessShortCode()
                        + " with account reference " + order.getAccountReference()
        );
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found: " + tenantId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    private DarajaCredentials platformCredentials() {
        return DarajaCredentials.of(
                darajaProperties.getConsumerKey(),
                darajaProperties.getConsumerSecret(),
                darajaProperties.getBusinessShortCode(),
                darajaProperties.getPasskey()
        );
    }
}
