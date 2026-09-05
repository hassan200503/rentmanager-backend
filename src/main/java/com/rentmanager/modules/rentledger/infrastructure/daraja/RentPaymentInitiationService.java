package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Initiates an M-Pesa STK push for an outstanding {@code RentLedgerEntry}.
 * Deliberately reuses {@code DarajaService} and {@code DarajaProperties}
 * directly from the reservation module rather than duplicating a second
 * Daraja API client — both are already generic (DarajaService takes
 * credentials as a parameter; DarajaProperties is a plain
 * {@code @ConfigurationProperties} bean), so this is a reuse of a shared
 * infrastructure utility, not a business-logic coupling between modules.
 * Worth revisiting as a move to a shared/common package if a third
 * Daraja-calling module ever appears — not done here since only two exist.
 *
 * CREDENTIAL SOURCE — decided by the landlord's {@code CollectionMode}:
 *
 * <ul>
 *   <li><b>DIRECT</b> (default): signs with the landlord's OWN Daraja
 *       credentials, so the money settles into their own paybill and the
 *       platform never receives it. Same path the reservation/deposit flow
 *       has always used. Refuses when the landlord has not configured
 *       credentials rather than falling back to the platform's — a refusal
 *       is a support ticket; a silent fallback is unlicensed aggregation.</li>
 *   <li><b>PLATFORM_CUSTODY</b> (legacy, requires CBK authorisation): signs
 *       with the platform's credentials, so rent lands in the platform
 *       paybill for commission deduction and B2C disbursement.</li>
 * </ul>
 *
 * <p>This used to be unconditionally the platform's credentials. That made
 * every landlord an aggregation arrangement by default — see V89 and
 * {@code CollectionMode} for why that is the fact that triggers licensing.
 *
 * PAYMENT AMOUNT: charges the entry's full {@code getBalanceOwed()}, not a
 * caller-supplied amount — this endpoint is for "pay what's currently
 * owed", not partial/arbitrary payments. A partial-payment path can be
 * added later as a separate, explicit method if needed rather than
 * overloading this one silently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentInitiationService {

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final LeaseRepository leaseRepository;
    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final DarajaService darajaService;
    private final DarajaProperties darajaProperties;
    private final PlatformDarajaCredentialsResolver darajaResolver;
    private final com.rentmanager.modules.tenant.domain.repository.TenantRepository tenantRepository;

    @Transactional
    public RentPaymentRequest initiate(
            UUID tenantId,
            UUID rentLedgerEntryId,
            String mpesaPhone
    ) {
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(rentLedgerEntryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "rent ledger entry not found: " + rentLedgerEntryId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        BigDecimal amount = entry.getBalanceOwed();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RentLedgerStateException(
                    "rent ledger entry has no outstanding balance: " + rentLedgerEntryId,
                    ErrorCode.RENT_LEDGER_ENTRY_ALREADY_SETTLED
            );
        }

        UUID leaseId = entry.getLeaseId();
        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "lease not found: " + leaseId,
                        ErrorCode.LEASE_NOT_FOUND
                ));

        return doInitiate(tenantId, lease, rentLedgerEntryId, amount, mpesaPhone, entry.getCurrency());
    }

    @Transactional
    public RentPaymentRequest initiateWithAmount(
            UUID tenantId,
            UUID rentLedgerEntryId,
            BigDecimal amount,
            String mpesaPhone
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RentLedgerStateException(
                    "payment amount must be > 0",
                    ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT
            );
        }

        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(rentLedgerEntryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "rent ledger entry not found: " + rentLedgerEntryId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        UUID leaseId = entry.getLeaseId();
        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "lease not found: " + leaseId,
                        ErrorCode.LEASE_NOT_FOUND
                ));

        return doInitiate(tenantId, lease, rentLedgerEntryId, amount, mpesaPhone, entry.getCurrency());
    }

    /**
     * How long a just-sent STK push is treated as still live, so a second
     * one is not sent alongside it.
     *
     * <p>Deliberately short. The risk being closed is <em>concurrent</em>
     * prompts — a renter double-tapping Pay, or reloading the page and
     * starting again — because two prompts a renter can both complete
     * produce two genuine M-Pesa receipts and therefore a real overpayment,
     * recoverable afterwards through the OVERPAID admin resolution but not
     * prevented. It is explicitly NOT meant to stop the next day's auto-pay
     * retry: rent that is still owed tomorrow should be asked for again.
     *
     * <p>It has to expire, and quickly. There is no stale-request sweep for
     * rent payments (see {@code RentPaymentRequestStatus}), so a renter who
     * simply cancels the prompt leaves a PENDING row behind forever — a
     * window without an expiry would lock them out of paying at all, which
     * is a worse failure than the one being fixed.
     */
    private static final Duration LIVE_PROMPT_WINDOW = Duration.ofMinutes(3);

    private RentPaymentRequest doInitiate(
            UUID tenantId,
            Lease lease,
            UUID rentLedgerEntryId,
            BigDecimal amount,
            String mpesaPhone,
            String currency
    ) {
        RentPaymentRequest live = rentPaymentRequestRepository
                .findLatestPendingForEntry(tenantId, rentLedgerEntryId)
                .filter(r -> r.getCreatedAt() != null
                        && r.getCreatedAt().isAfter(Instant.now().minus(LIVE_PROMPT_WINDOW)))
                .orElse(null);

        if (live != null) {
            log.info("Rent payment STK push suppressed — a prompt sent {} is still live. "
                            + "leaseId={} rentLedgerEntryId={} existingRequestId={}",
                    live.getCreatedAt(), lease.getId(), rentLedgerEntryId, live.getId());
            return live;
        }

        RentPaymentRequest request = RentPaymentRequest.create(tenantId, lease.getId(), rentLedgerEntryId, amount, currency);
        request = rentPaymentRequestRepository.save(request);

        DarajaCredentials credentials = resolveCredentialsFor(tenantId);

        String checkoutRequestId = darajaService.initiateSTKPush(
                mpesaPhone,
                amount,
                lease.getLeaseNumber(),
                "Rent payment",
                credentials,
                darajaProperties.getRentPaymentCallbackUrl()
        );

        request.attachCheckoutRequestId(checkoutRequestId);
        request = rentPaymentRequestRepository.save(request);

        log.info("Rent payment STK push initiated. leaseId={} rentLedgerEntryId={} amount={} checkoutRequestId={}",
                lease.getId(), rentLedgerEntryId, amount, checkoutRequestId);

        return request;
    }

    /**
     * Chooses whose M-Pesa account this rent payment lands in.
     *
     * <p>Deliberately fails closed toward DIRECT: a landlord we cannot read,
     * or one with no explicit mode, is treated as collecting directly. The
     * failure mode of guessing wrong in that direction is a refused payment
     * and a clear message. The failure mode of guessing wrong the other way
     * is collecting a stranger's rent into the platform's account without a
     * licence.
     */
    private DarajaCredentials resolveCredentialsFor(UUID tenantId) {
        Tenant landlord = tenantRepository.findById(tenantId).orElse(null);

        if (landlord != null && !landlord.collectsDirectly()) {
            // PLATFORM_CUSTODY. Resolved through the Integration Registry
            // (database config first, legacy daraja.* environment fallback).
            log.info("Rent STK push using PLATFORM custody credentials. tenantId={}", tenantId);
            return darajaResolver.stkCredentials();
        }

        DarajaCredentials own = landlord == null ? null : landlord.getDarajaCredentials();
        if (own == null || !own.isConfigured()) {
            throw new RentLedgerStateException(
                    "This landlord has not finished M-Pesa setup, so rent cannot be collected yet. "
                            + "Add your Daraja credentials under Payment settings and press "
                            + "Test connection.",
                    ErrorCode.VALIDATION_ERROR);
        }
        return own;
    }
}
