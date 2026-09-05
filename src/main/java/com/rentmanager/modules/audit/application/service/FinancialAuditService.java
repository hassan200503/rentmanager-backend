package com.rentmanager.modules.audit.application.service;

import com.rentmanager.modules.audit.domain.enums.AuditAction;
import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The recording side of every action that moves money.
 *
 * <h2>Why a dedicated entry point</h2>
 * {@link AuditService#record} takes an eleven-argument constructor, and a
 * money path that has to assemble one correctly at each call site will
 * eventually assemble one wrongly — or skip it under time pressure. This
 * narrows the surface to a few named methods that cannot omit the actor.
 *
 * <h2>Never throws</h2>
 * A failure to write the audit row must not roll back the money operation it
 * describes, and must not surface to the caller as an error about something
 * they did not do. Failures are logged loudly instead. That is a deliberate
 * trade: an unrecorded payout is bad, a payout that fails because logging
 * broke is worse, and a payout that silently half-happened is worst of all.
 *
 * <p>The counterpart to that choice is the loud log line — an audit gap has
 * to be discoverable, which is precisely the property the previous
 * implementation lacked when it returned success and wrote nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAuditService {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuditService auditService;

    /** A payout was raised against a ledger entry. */
    public void disbursementInitiated(
            UUID tenantId, UUID disbursementId, UUID ledgerEntryId,
            String amount, String recipientMasked, String correlationId) {

        record(AuditAction.DISBURSEMENT_INITIATED, tenantId, "DISBURSEMENT",
                disbursementId == null ? null : disbursementId.toString(),
                correlationId, SUCCESS,
                """
                {"amount":"%s","recipient":"%s","ledgerEntryId":"%s"}"""
                        .formatted(amount, recipientMasked, ledgerEntryId));
    }

    /**
     * A payout was refused before any money moved.
     *
     * <p>Recorded as deliberately as a success. A run of refusals against a
     * single account — amounts above the entitlement, or attempts while no
     * payout number is set — is the shape an attempt to drain funds makes,
     * and it is invisible if only successes are written down.
     */
    public void disbursementRefused(
            UUID tenantId, UUID ledgerEntryId, String requestedAmount, String reason) {

        record(AuditAction.DISBURSEMENT_FAILED, tenantId, "LEDGER_ENTRY",
                ledgerEntryId == null ? null : ledgerEntryId.toString(),
                null, FAILED,
                """
                {"requestedAmount":"%s","reason":"%s"}"""
                        .formatted(requestedAmount, escape(reason)));
    }

    /**
     * A human pointed an orphan M-Pesa receipt at a lease.
     *
     * <p>Both the receipt and the destination are recorded, because the
     * question a dispute asks is not "was a payment matched" but "who decided
     * it belonged to this lease rather than that one".
     */
    public void unmatchedPaymentResolved(
            UUID tenantId, String transactionId, UUID ledgerEntryId, String amount) {

        record(AuditAction.UNMATCHED_PAYMENT_RESOLVED, tenantId, "RENT_TRANSACTION",
                transactionId, null, SUCCESS,
                """
                {"appliedToLedgerEntryId":"%s","amount":"%s"}"""
                        .formatted(ledgerEntryId, amount));
    }

    /**
     * A failed payout was replayed by a platform owner or by the retry sweep.
     *
     * <p>Recorded separately from the original initiation. A retry is a fresh
     * money movement authorised by a different person at a different time, and
     * collapsing it into the original would hide who actually released it.
     */
    public void disbursementRetried(
            UUID tenantId, UUID disbursementId, String amount, String recipientMasked) {

        record(AuditAction.DISBURSEMENT_INITIATED, tenantId, "DISBURSEMENT",
                disbursementId == null ? null : disbursementId.toString(),
                null, SUCCESS,
                """
                {"retryOf":"%s","amount":"%s","recipient":"%s"}"""
                        .formatted(disbursementId, amount, recipientMasked));
    }

    /** A retry was refused before any money moved. */
    public void disbursementRetryRefused(
            UUID tenantId, UUID disbursementId, String reason) {

        record(AuditAction.DISBURSEMENT_FAILED, tenantId, "DISBURSEMENT",
                disbursementId == null ? null : disbursementId.toString(),
                null, FAILED,
                """
                {"stage":"retry","reason":"%s"}""".formatted(escape(reason)));
    }

    /**
     * The commission rate applied to a landlord's rent changed.
     *
     * <p>{@code landlordOrgId} is null for the platform-wide default, which is
     * the more consequential of the two: it silently changes what every
     * landlord without an override pays on every future payment.
     *
     * <p>Rates are recorded as before-and-after because "who lowered this
     * landlord's rate to zero, and when" is the question this row exists to
     * answer, and the policy rows themselves only show the current state.
     */
    public void commissionPolicyChanged(
            UUID landlordOrgId, String previousRatePercent, String newRatePercent) {

        record(AuditAction.COMMISSION_POLICY_CHANGED, landlordOrgId,
                landlordOrgId == null ? "PLATFORM_DEFAULT" : "TENANT",
                landlordOrgId == null ? "default" : landlordOrgId.toString(),
                null, SUCCESS,
                """
                {"scope":"%s","fromRatePercent":"%s","toRatePercent":"%s"}"""
                        .formatted(
                                landlordOrgId == null ? "platform-default" : "landlord-override",
                                previousRatePercent == null ? "none" : previousRatePercent,
                                newRatePercent == null ? "cleared" : newRatePercent));
    }

    /**
     * Payment provider credentials were added or rotated.
     *
     * <p>No credential material is recorded — only that a rotation happened,
     * for whom, and by whom. The point of the row is to make an unexpected
     * rotation visible, and a rotation is exactly the moment where recording
     * too much would create the leak it is meant to detect.
     */
    public void paymentCredentialsChanged(UUID tenantId, String provider, String environment) {
        record(AuditAction.PAYMENT_CREDENTIALS_CHANGED, tenantId, "INTEGRATION",
                provider, null, SUCCESS,
                """
                {"provider":"%s","environment":"%s"}"""
                        .formatted(escape(provider), escape(environment)));
    }

    /** The destination for every future payout on this account changed. */
    public void payoutDestinationChanged(
            UUID tenantId, String previousMasked, String newMasked) {

        record(AuditAction.PAYOUT_DESTINATION_CHANGED, tenantId, "TENANT",
                tenantId == null ? null : tenantId.toString(), null, SUCCESS,
                """
                {"from":"%s","to":"%s"}""".formatted(previousMasked, newMasked));
    }

    private void record(
            AuditAction action, UUID tenantId, String entityType, String entityId,
            String correlationId, String status, String metadata) {
        try {
            auditService.record(new AuditLog(
                    tenantId,
                    action.name(),
                    currentActorId(),
                    currentActorType(),
                    entityType,
                    entityId,
                    correlationId,
                    status,
                    metadata,
                    null,
                    null
            ));
        } catch (Exception e) {
            // Loud on purpose. An audit gap that nobody notices is the same
            // situation this module was in before it persisted anything.
            log.error("AUDIT WRITE FAILED — action={} tenantId={} entityId={} was not recorded",
                    action, tenantId, entityId, e);
        }
    }

    /**
     * The Clerk user id from the verified JWT, or {@code SYSTEM} for
     * scheduler work running outside a request.
     */
    private static String currentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return SYSTEM_ACTOR;
        }
        return auth.getName();
    }

    private static String currentActorType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return SYSTEM_ACTOR;
        }
        return auth.getAuthorities().stream()
                .map(Object::toString)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("UNKNOWN");
    }

    /** Keeps a failure reason from breaking the JSON it is embedded in. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
