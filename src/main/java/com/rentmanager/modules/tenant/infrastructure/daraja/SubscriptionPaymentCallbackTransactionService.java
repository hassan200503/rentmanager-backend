package com.rentmanager.modules.tenant.infrastructure.daraja;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.config.SubscriptionBillingProperties;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Transactional half of the subscription payment callback handling:
 * idempotency (status guard + @Version optimistic locking, same pattern
 * as {@code RentPaymentCallbackTransactionService}), then application of
 * the payment to the landlord's subscription state.
 *
 * <p>Late successes are honored: a confirmed M-Pesa receipt is applied
 * even if the stale-request sweep had already marked the request
 * EXPIRED (or a failure callback had marked it FAILED) - the money moved,
 * and locking a paying landlord out of their paid premium period is not
 * acceptable. Terminal-failure statuses are therefore treated as
 * "not yet PAID" for the purpose of the success path.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPaymentCallbackTransactionService {

    private final SubscriptionPaymentRequestRepository paymentRequestRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionBillingProperties properties;
    private final EntityManager entityManager;

    @Transactional
    public void processSuccessfulCallback(
            String checkoutRequestId, String mpesaReceiptNumber
    ) {
        SubscriptionPaymentRequest request = paymentRequestRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No SubscriptionPaymentRequest found for CheckoutRequestID: " + checkoutRequestId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (request.getStatus() == SubscriptionPaymentRequestStatus.PAID) {
            log.info("Duplicate subscription payment callback - skipping. CheckoutRequestID={} status={}",
                    checkoutRequestId, request.getStatus());
            return;
        }

        request.markPaid(mpesaReceiptNumber);
        try {
            paymentRequestRepository.save(request);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure marking subscription payment PAID. " +
                    "CheckoutRequestID={} requestId={} - likely concurrent callback delivery, " +
                    "will self-heal on Safaricom retry.",
                    checkoutRequestId, request.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error persisting paid subscription payment. " +
                    "CheckoutRequestID={} requestId={}",
                    checkoutRequestId, request.getId(), e);
            throw e;
        }

        applyToSubscription(request);
    }

    @Transactional
    public void processFailedCallback(String checkoutRequestId, String reason) {
        SubscriptionPaymentRequest request = paymentRequestRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElse(null);

        if (request == null) {
            log.error("No SubscriptionPaymentRequest found for failed callback. CheckoutRequestID={}",
                    checkoutRequestId);
            return;
        }

        if (request.getStatus() != SubscriptionPaymentRequestStatus.PENDING) {
            log.info("Duplicate failure callback - already processed. CheckoutRequestID={} status={}",
                    checkoutRequestId, request.getStatus());
            return;
        }

        request.markFailed(reason);
        try {
            paymentRequestRepository.save(request);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure marking subscription payment FAILED. " +
                    "CheckoutRequestID={} requestId={} - likely concurrent callback delivery, " +
                    "will self-heal on Safaricom retry.",
                    checkoutRequestId, request.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error persisting failed subscription payment. " +
                    "CheckoutRequestID={} requestId={}",
                    checkoutRequestId, request.getId(), e);
            throw e;
        }

        log.warn("Subscription payment failed. CheckoutRequestID={} Reason={}", checkoutRequestId, reason);

        if (request.isRenewal()) {
            enterGracePeriod(request.getTenantId());
        }
    }

    /**
     * Applies a paid subscription payment to the tenant:
     * <ul>
     *   <li>INITIAL_ACTIVATION: switches the landlord to PREMIUM_MONTHLY
     *       (first successful payment = the switch takes effect).</li>
     *   <li>RENEWAL: extends the paid period by one month and clears any
     *       grace state.</li>
     * </ul>
     */
    private void applyToSubscription(SubscriptionPaymentRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found for subscription payment: " + request.getTenantId(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (request.isRenewal()) {
            if (!tenant.isPremiumBilling()) {
                log.warn("Renewal payment arrived for a non-premium tenant - extending skipped. " +
                        "tenantId={} requestId={}", tenant.getId(), request.getId());
                return;
            }
            LocalDate nextEnd = tenant.getPlanEndDate() != null
                    ? tenant.getPlanEndDate().plusMonths(1)
                    : LocalDate.now().plusMonths(1);
            tenant.extendPremiumSubscription(nextEnd);
            tenantRepository.save(tenant);
            log.info("Premium subscription renewed. tenantId={} newPlanEndDate={}", tenant.getId(), nextEnd);
            return;
        }

        if (tenant.isPremiumBilling()) {
            log.warn("Initial activation payment arrived for an already-premium tenant - skipped. " +
                    "tenantId={} requestId={}", tenant.getId(), request.getId());
            return;
        }

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(1);
        tenant.activatePremiumSubscription(request.getSubscriptionPlanId(), start, end);
        tenantRepository.save(tenant);
        log.info("Premium monthly billing activated. tenantId={} planId={} period=[{}, {}]",
                tenant.getId(), request.getSubscriptionPlanId(), start, end);
    }

    /**
     * Enters the grace window for a premium tenant whose renewal payment
     * failed or timed out. Premium benefits (zero commission on rent
     * payments) continue during grace; the scheduler reverts to COMMISSION
     * when the window ends. Called from the failure callback AND from the
     * stale-request sweep (no callback arrived at all).
     */
    @Transactional
    public void enterGracePeriod(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElse(null);
        if (tenant == null || !tenant.isPremiumBilling()
                || tenant.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            log.info("Grace period entry skipped for tenantId={} (not active premium)", tenantId);
            return;
        }

        LocalDate graceEndsAt = LocalDate.now().plusDays(properties.getGraceDays());
        tenant.enterPremiumGracePeriod(graceEndsAt);
        tenantRepository.save(tenant);
        log.warn("Premium subscription entered grace period. tenantId={} graceEndsAt={}",
                tenantId, graceEndsAt);
    }
}
