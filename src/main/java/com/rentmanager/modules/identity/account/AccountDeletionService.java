package com.rentmanager.modules.identity.account;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.notification.push.application.NotificationPreferenceService;
import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Self-service account deletion (App Store 5.1.1(v), Google Play account
 * deletion policy).
 *
 * <h3>What is deleted</h3>
 * The person's login (Clerk user), their push devices and preferences, and the
 * identity on their local account row (Clerk id, email and name replaced with
 * tombstones). Renter profiles are detached from the login.
 *
 * <h3>What is kept, and why</h3>
 * A landlord's records of a tenancy — the renter profile's name and contact
 * details, leases, rent ledger and payments — are the landlord's business and
 * tax records, which RentManager holds on the landlord's behalf. They are not
 * the renter's account and are not erased here. Whether any of those must be
 * erased on request is a data-protection question for the landlord (the
 * controller of that data) and is flagged for legal review, not decided in code.
 *
 * <h3>Owners</h3>
 * The owner of a landlord organisation cannot be removed automatically: the
 * organisation, its staff and its renters' records all hang off it. The
 * request is recorded as PENDING_REVIEW so it is handled, not lost.
 *
 * <h3>Order</h3>
 * Clerk first. If Clerk refuses, nothing local is touched and the person is
 * asked to retry. Local erasure runs in one transaction after Clerk succeeds;
 * if that transaction fails, the request row records FAILED and the error is
 * logged for manual completion — the login is already gone, so the person
 * cannot be left half-signed-in.
 */
@Slf4j
@Service
public class AccountDeletionService {

    public enum Outcome { COMPLETED, PENDING_REVIEW }

    public record Result(Outcome outcome, String message) {}

    private final ClerkService clerkService;
    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final PushDeviceService pushDeviceService;
    private final NotificationPreferenceService preferenceService;
    private final AccountDeletionRequestStore requests;
    private final TransactionTemplate transactions;

    public AccountDeletionService(
            ObjectProvider<ClerkService> clerkService,
            UserRepository userRepository,
            TenantProfileRepository tenantProfileRepository,
            PushDeviceService pushDeviceService,
            NotificationPreferenceService preferenceService,
            AccountDeletionRequestStore requests,
            TransactionTemplate transactions
    ) {
        this.clerkService = clerkService.getIfAvailable();
        this.userRepository = userRepository;
        this.tenantProfileRepository = tenantProfileRepository;
        this.pushDeviceService = pushDeviceService;
        this.preferenceService = preferenceService;
        this.requests = requests;
        this.transactions = transactions;
    }

    public Result requestDeletion(String clerkUserId) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("clerkUserId is required");
        }

        Optional<User> user = userRepository.findByClerkUserId(clerkUserId);
        if (user.isPresent() && user.get().getRole() == UserRole.OWNER && user.get().getTenantId() != null) {
            requests.record(clerkUserId, "APP", "PENDING_REVIEW",
                    "Owner of landlord organisation " + user.get().getTenantId());
            return new Result(Outcome.PENDING_REVIEW,
                    "You own a landlord organisation, so your account can't be removed automatically. "
                            + "We've recorded your request and the RentManager team will contact you to close "
                            + "or hand over the organisation first.");
        }

        if (clerkService == null) {
            throw new IllegalStateException("Identity provider is not configured");
        }
        try {
            clerkService.deleteUserStrict(clerkUserId);
        } catch (RuntimeException ex) {
            requests.record(clerkUserId, "APP", "FAILED", "Identity provider refused: " + ex.getClass().getSimpleName());
            log.error("Account deletion: Clerk deletion failed; nothing erased locally");
            throw new BusinessException(
                    "We couldn't delete your account just now. Nothing has been changed. Please try again.",
                    ErrorCode.BUSINESS_ERROR);
        }

        eraseLocalIdentity(clerkUserId, "APP");
        return new Result(Outcome.COMPLETED,
                "Your account has been deleted. Your landlord keeps their own records of your tenancy and payments.");
    }

    /**
     * Local erasure only. Also used when Clerk reports the user was deleted
     * (webhook), where there is no login left to remove.
     */
    public void eraseLocalIdentity(String clerkUserId, String source) {
        UUID requestId = UUID.randomUUID();
        String tombstone = "deleted:" + requestId;
        try {
            transactions.executeWithoutResult(status -> {
                int devices = pushDeviceService.revokeAllFor(clerkUserId);
                preferenceService.deleteAll(clerkUserId);

                int profiles = 0;
                for (TenantProfile profile : tenantProfileRepository.findAllByClerkUserId(clerkUserId)) {
                    // Unique per landlord (tenant_id, clerk_user_id): suffix keeps tombstones distinct.
                    profile.unlinkIdentity(tombstone + ":" + profile.getId());
                    tenantProfileRepository.save(profile);
                    profiles++;
                }

                userRepository.findByClerkUserId(clerkUserId).ifPresent(u -> {
                    u.anonymiseForDeletion(tombstone);
                    userRepository.save(u);
                });

                requests.record(clerkUserId, source, "COMPLETED",
                        "Revoked " + devices + " device(s); unlinked " + profiles + " renter profile(s)");
            });
        } catch (RuntimeException ex) {
            log.error("Account deletion: local erasure FAILED after login removal — manual completion required. requestSource={}",
                    source, ex);
            requests.record(clerkUserId, source, "FAILED", "Local erasure failed: " + ex.getClass().getSimpleName());
            throw ex;
        }
    }

    public Optional<AccountDeletionRequestStore.Latest> latest(String clerkUserId) {
        return requests.latest(clerkUserId);
    }
}
