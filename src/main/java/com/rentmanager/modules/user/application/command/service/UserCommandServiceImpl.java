package com.rentmanager.modules.user.application.command.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.user.application.dto.request.InviteUserRequest;
import com.rentmanager.modules.user.application.dto.response.InviteUserResponse;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final ClerkService clerkService;
    private final SmsService smsService;

    @Override
    @Transactional
    public InviteUserResponse inviteUser(InviteUserRequest request) {

        UUID inviterUserId = TenantContext.getUserId();
        if (inviterUserId == null) {
            throw new AccessDeniedException("Authenticated user could not be resolved for this request");
        }

        UUID tenantId = TenantContext.getTenantId(); // throws IllegalStateException if unresolved

        User inviter = userRepository.findById(inviterUserId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user could not be resolved for this request"));

        enforceInviteMatrix(inviter.getRole(), request.getRole());

        String fullName = request.getFirstName() + " " + request.getLastName();
        String password = generateTemporaryPassword();

        ClerkUserCreationResult clerkResult = clerkService.createStaffUser(
                fullName,
                request.getEmail(),
                request.getPhone(),
                password
        );

        // Per confirmed product decision: any pre-existing Clerk account
        // for this email blocks the invite outright, regardless of which
        // tenant (or no tenant, e.g. a renter) it's associated with —
        // there is no safe way to deliver credentials for a reused
        // account, so there is no working path to distinguish further.
        if (!clerkResult.newlyCreated()) {
            log.warn("Invite rejected — email already has an existing account. email={}", request.getEmail());
            throw new IllegalStateException("This email is already associated with an existing account");
        }

        String clerkUserId = clerkResult.clerkUserId();

        User newUser = User.createInvited(
                clerkUserId,
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                tenantId,
                request.getRole()
        );

        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (Exception e) {
            // Compensate: undo the just-created Clerk account, since this
            // run created it and the local write failed. Single-step
            // compensation, not a saga — no other side effects have
            // occurred yet at this point.
            log.error("Local user save failed after Clerk account creation — compensating. clerkUserId={}",
                    clerkUserId, e);
            clerkService.deleteUser(clerkUserId);
            throw e;
        }

        smsService.sendCredentials(request.getPhone(), password);

        log.info("User invited. userId={} tenantId={} role={} invitedBy={}",
                savedUser.getId(), tenantId, request.getRole(), inviterUserId);

        return new InviteUserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
    }

    /**
     * OWNER may invite MANAGER or STAFF. MANAGER may invite STAFF only —
     * never MANAGER or OWNER (prevents a manager from creating peers or
     * superiors). STAFF cannot invite at all; in practice this is already
     * blocked at the controller via @PreAuthorize, but this check stays
     * here too since it's the only place with the data (inviter's actual
     * role) to enforce the MANAGER-tier restriction correctly.
     */
    private void enforceInviteMatrix(UserRole inviterRole, UserRole requestedRole) {

        if (inviterRole == UserRole.OWNER) {
            return;
        }

        if (inviterRole == UserRole.MANAGER) {
            if (requestedRole != UserRole.STAFF) {
                throw new AccessDeniedException("Managers may only invite staff-level users");
            }
            return;
        }

        throw new AccessDeniedException("You do not have permission to invite users");
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}