package com.rentmanager.modules.tenant.application.service;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderFrequency;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionStandingOrderRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * M-Pesa Ratiba (standing order) onboarding for the premium billing flow.
 *
 * <p>Merchant-initiated model (Daraja {@code createStandingOrderExternal}):
 * the dashboard shows the Paybill number, the unique account reference
 * (the landlord's tenant code, max 12 chars per Ratiba) and the fee, and
 * offers a one-tap "Set up automatic payments" - Safaricom then sends the
 * landlord an NI push (PIN prompt) which is the consent + MSISDN ownership
 * check, and reports the final result asynchronously to the Ratiba
 * callback endpoint ({@code handleCreationCallback}). The manual
 * {@code *334#} / M-Pesa app route remains as a fallback for landlords who
 * prefer self-serve; both paths converge on the same C2B account reference.
 *
 * <p>Ratiba is a commercial Daraja API (Safaricom contract required for
 * Go Live), so the whole integration is feature-flagged off by default via
 * {@code daraja.ratiba-enabled}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatibaStandingOrderService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionStandingOrderRepository standingOrderRepository;
    private final DarajaService darajaService;
    private final DarajaProperties darajaProperties;

    /**
     * Initiates a Ratiba standing-order creation for a premium landlord.
     * Requires an active premium subscription with a monthly-priced plan
     * and a tenant code short enough to serve as the C2B account reference.
     *
     * @return the PENDING_AUTHORIZATION standing order record
     */
    @Transactional
    public SubscriptionStandingOrder createStandingOrder(UUID tenantId) {
        if (!darajaProperties.isRatibaEnabled()) {
            throw new BusinessException(
                    "Ratiba standing orders are not enabled yet - use the M-Pesa *334# manual setup "
                            + "with the Paybill number, account reference and fee shown in the dashboard, "
                            + "or contact support",
                    ErrorCode.SUBSCRIPTION_RATIBA_UNAVAILABLE
            );
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found: " + tenantId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (!tenant.isPremiumBilling()) {
            throw new BusinessException(
                    "Landlord is not on premium monthly billing: " + tenantId,
                    ErrorCode.SUBSCRIPTION_NOT_PREMIUM
            );
        }
        if (tenant.getPlanEndDate() == null) {
            throw new BusinessException(
                    "Premium subscription has no active paid period yet - complete the first payment first",
                    ErrorCode.SUBSCRIPTION_PLAN_PRICE_REQUIRED
            );
        }

        String accountReference = tenant.getTenantCode();
        if (accountReference == null || accountReference.isBlank()
                || accountReference.length() > 12) {
            throw new BusinessException(
                    "Tenant code '" + accountReference + "' cannot be used as the M-Pesa account "
                            + "reference - Ratiba requires at most 12 characters. Contact support to "
                            + "set up automatic payments",
                    ErrorCode.SUBSCRIPTION_RATIBA_UNAVAILABLE
            );
        }

        SubscriptionPlan plan = tenant.getSubscriptionPlanId() != null
                ? subscriptionPlanRepository.findById(tenant.getSubscriptionPlanId()).orElse(null)
                : null;
        BigDecimal amount = plan != null ? plan.getMonthlyPrice() : null;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Subscription plan has no monthly price - cannot set up a standing order",
                    ErrorCode.SUBSCRIPTION_PLAN_PRICE_REQUIRED
            );
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(
                    "Plan monthly price " + amount + " is not a whole number of shillings - "
                            + "Ratiba standing orders do not support decimals",
                    ErrorCode.SUBSCRIPTION_RATIBA_UNAVAILABLE
            );
        }

        SubscriptionStandingOrder order = SubscriptionStandingOrder.create(
                tenantId,
                accountReference,
                amount,
                StandingOrderFrequency.MONTHLY,
                tenant.getPlanEndDate(),
                tenant.getPlanEndDate().plusYears(1)
        );
        order = standingOrderRepository.save(order);

        String responseRefId;
        try {
            responseRefId = darajaService.createStandingOrder(
                    tenant.getPhoneNumber(),
                    amount,
                    accountReference,
                    tenant.getPlanEndDate(),
                    tenant.getPlanEndDate().plusYears(1),
                    darajaProperties.getRatibaCallbackUrl()
            );
        } catch (DarajaException e) {
            order.markFailed("Daraja rejected the standing order creation: " + e.getMessage());
            standingOrderRepository.save(order);
            throw e;
        }

        order.attachResponseRefId(responseRefId);
        standingOrderRepository.save(order);

        log.info("Ratiba standing order creation initiated. tenantId={} accountReference={} "
                        + "amount={} orderId={} responseRefID={}",
                tenantId, accountReference, amount, order.getId(), responseRefId);
        return order;
    }

    /**
     * Applies the async Daraja creation callback (createStandingOrderExternal
     * result). Idempotent: an already-resolved order is left untouched.
     */
    @Transactional
    public void handleCreationCallback(
            String responseRefId,
            boolean successful,
            String transactionId,
            String failureReason
    ) {
        SubscriptionStandingOrder order = standingOrderRepository
                .findByRatibaResponseRefId(responseRefId)
                .orElse(null);
        if (order == null) {
            log.warn("Ratiba creation callback for unknown responseRefID={} - ignored", responseRefId);
            return;
        }
        if (order.getStatus() != StandingOrderStatus.PENDING_AUTHORIZATION) {
            log.info("Ratiba order already resolved, skipping callback. orderId={} status={}",
                    order.getId(), order.getStatus());
            return;
        }

        if (successful) {
            order.markActive(transactionId);
            log.info("Ratiba standing order activated. orderId={} tenantId={} transactionId={}",
                    order.getId(), order.getTenantId(), transactionId);
        } else {
            order.markFailed(failureReason != null ? failureReason : "Ratiba creation rejected");
            log.warn("Ratiba standing order creation failed. orderId={} tenantId={} reason={}",
                    order.getId(), order.getTenantId(), failureReason);
        }
        standingOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionStandingOrder> findLatest(UUID tenantId) {
        return standingOrderRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
