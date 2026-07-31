package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.daraja.C2BPaymentConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Applies inbound C2B (Paybill) confirmations to premium subscriptions.
 * This is the Ratiba collection leg: each executed standing-order payment
 * carries the landlord's tenant code in {@code BillRefNumber}, which maps
 * 1:1 to a tenant. A payment that matches the account reference AND the
 * plan's exact monthly fee extends the paid period by one billing cycle
 * (from the current period anchor - a late payment during grace therefore
 * covers the overdue period rather than silently moving the anchor).
 *
 * <p>Fail-closed posture, same as the rest of the financial handling in
 * this codebase: an unrecognized reference, a non-premium tenant, or a
 * wrong amount is NEVER applied - it is captured in {@code unmatched_payments}
 * for manual reconciliation. Idempotent on TransID (Daraja may redeliver).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionC2bPaymentService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentRequestRepository paymentRequestRepository;
    private final UnmatchedPaymentRepository unmatchedPaymentRepository;

    @Transactional
    public void handleConfirmation(C2BPaymentConfirmationPayload payload) {
        if (payload.transId() == null || payload.transId().isBlank()
                || payload.transAmount() == null
                || payload.transAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("C2B confirmation ignored - missing TransID or invalid amount. payload={}", payload);
            return;
        }

        if (paymentRequestRepository.findByMpesaReceiptNumber(payload.transId()).isPresent()) {
            log.info("C2B confirmation already applied, skipping. transId={}", payload.transId());
            return;
        }

        String accountReference = payload.billRefNumber();
        if (accountReference == null || accountReference.isBlank()) {
            recordUnmatched(null, payload, "C2B payment with no account reference");
            return;
        }

        Tenant tenant = tenantRepository.findByTenantCode(accountReference).orElse(null);
        if (tenant == null) {
            recordUnmatched(null, payload,
                    "Unknown account reference - no landlord with tenant code " + accountReference);
            return;
        }

        if (!tenant.isPremiumBilling()) {
            recordUnmatched(tenant.getId(), payload,
                    "Landlord is not on premium billing - payment cannot be applied");
            return;
        }

        SubscriptionPlan plan = tenant.getSubscriptionPlanId() != null
                ? subscriptionPlanRepository.findById(tenant.getSubscriptionPlanId()).orElse(null)
                : null;
        BigDecimal expected = plan != null ? plan.getMonthlyPrice() : null;
        if (expected == null) {
            recordUnmatched(tenant.getId(), payload,
                    "No monthly price on the landlord's subscription plan - payment cannot be applied");
            return;
        }

        if (payload.transAmount().compareTo(expected) != 0) {
            recordUnmatched(tenant.getId(), payload,
                    "Amount mismatch - expected " + expected + " but received " + payload.transAmount());
            return;
        }

        applyPayment(tenant, plan, payload);
    }

    private void applyPayment(Tenant tenant, SubscriptionPlan plan, C2BPaymentConfirmationPayload payload) {
        SubscriptionPaymentRequest request = SubscriptionPaymentRequest.create(
                tenant.getId(),
                plan.getId(),
                payload.transAmount(),
                payload.msisdn() != null ? payload.msisdn() : tenant.getPhoneNumber(),
                SubscriptionPaymentPurpose.RENEWAL
        );
        request.markPaid(payload.transId());
        paymentRequestRepository.save(request);

        LocalDate anchor = tenant.getPlanEndDate() != null
                ? tenant.getPlanEndDate()
                : LocalDate.now();
        tenant.extendPremiumSubscription(anchor.plusMonths(1));
        tenantRepository.save(tenant);

        log.info("Ratiba/C2B payment applied to premium subscription. tenantId={} transId={} "
                        + "amount={} newPeriodEnd={}",
                tenant.getId(), payload.transId(), payload.transAmount(), tenant.getPlanEndDate());
    }

    private void recordUnmatched(UUID tenantId, C2BPaymentConfirmationPayload payload, String reason) {
        UnmatchedPayment unmatched = UnmatchedPayment.create(
                tenantId,
                payload.transId(),
                payload.transAmount(),
                payload.msisdn(),
                payload.billRefNumber(),
                LocalDateTime.now(),
                null,
                payload.transId(),
                null,
                "subscription-c2b: " + reason
        );
        unmatchedPaymentRepository.save(unmatched);
        log.warn("C2B payment flagged for manual reconciliation. tenantId={} transId={} "
                        + "amount={} billRefNumber={} reason={}",
                tenantId, payload.transId(), payload.transAmount(), payload.billRefNumber(), reason);
    }
}
