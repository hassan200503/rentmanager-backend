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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers §6.2 acceptance criteria for UserCommandServiceImplTest from the
 * RBAC handoff doc. All mocked collaborators (UserRepository, ClerkService,
 * SmsService) come from interfaces/impls actually shown in this
 * conversation; TenantContext is exercised as the real ThreadLocal-backed
 * utility (also shown verbatim), not mocked, since its statics are simple
 * enough to set up/tear down directly in each test.
 */
@ExtendWith(MockitoExtension.class)
class UserCommandServiceImplTest {

    private static final UUID INVITER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClerkService clerkService;

    @Mock
    private SmsService smsService;

    private UserCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserCommandServiceImpl(userRepository, clerkService, smsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User inviterWithRole(UserRole role) {
        return User.createInvited(
                "clerk_inviter_id", "inviter@example.com", "In", "Viter", TENANT_ID, role
        );
    }

    private InviteUserRequest requestFor(UserRole role) {
        InviteUserRequest request = new InviteUserRequest();
        request.setFirstName("New");
        request.setLastName("Hire");
        request.setEmail("newhire@example.com");
        request.setPhone("+254700000000");
        request.setRole(role);
        return request;
    }

    private void authenticateAs(UUID userId, UUID tenantId) {
        TenantContext.setUserId(userId);
        TenantContext.setTenantId(tenantId);
    }

    // --- Invite matrix: allowed combinations ---

    @Test
    void ownerInvitingManager_succeeds() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.OWNER)));
        when(clerkService.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClerkUserCreationResult("clerk_new_id", true));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteUserResponse response = service.inviteUser(requestFor(UserRole.MANAGER));

        assertThat(response.email()).isEqualTo("newhire@example.com");
        assertThat(response.role()).isEqualTo(UserRole.MANAGER);
        verify(smsService).sendCredentials(eq("+254700000000"), anyString());
        verify(clerkService, never()).deleteUser(anyString());
    }

    @Test
    void ownerInvitingStaff_succeeds() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.OWNER)));
        when(clerkService.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClerkUserCreationResult("clerk_new_id", true));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteUserResponse response = service.inviteUser(requestFor(UserRole.STAFF));

        assertThat(response.role()).isEqualTo(UserRole.STAFF);
    }

    @Test
    void managerInvitingStaff_succeeds() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.MANAGER)));
        when(clerkService.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClerkUserCreationResult("clerk_new_id", true));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteUserResponse response = service.inviteUser(requestFor(UserRole.STAFF));

        assertThat(response.role()).isEqualTo(UserRole.STAFF);
    }

    // --- Invite matrix: rejected combinations ---

    @Test
    void managerInvitingManager_rejected() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.MANAGER)));

        assertThatThrownBy(() -> service.inviteUser(requestFor(UserRole.MANAGER)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(clerkService);
        verifyNoInteractions(smsService);
    }

    @Test
    void managerInvitingOwner_rejected() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.MANAGER)));

        assertThatThrownBy(() -> service.inviteUser(requestFor(UserRole.OWNER)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(clerkService);
        verifyNoInteractions(smsService);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MANAGER", "OWNER", "STAFF"})
    void staffCannotInviteAnyone(UserRole requestedRole) {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.STAFF)));

        assertThatThrownBy(() -> service.inviteUser(requestFor(requestedRole)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(clerkService);
    }

    // --- Reuse / collision handling ---

    @Test
    void existingClerkAccount_rejected_noCompensationAttempted() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.OWNER)));
        when(clerkService.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClerkUserCreationResult("existing_clerk_id", false));

        assertThatThrownBy(() -> service.inviteUser(requestFor(UserRole.STAFF)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already associated with an existing account");

        // Nothing local was created yet, so there is nothing to compensate for.
        verify(clerkService, never()).deleteUser(anyString());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(smsService);
    }

    // --- Compensation on local save failure ---

    @Test
    void localSaveFails_afterClerkAccountCreated_compensatesAndRethrows() {
        authenticateAs(INVITER_ID, TENANT_ID);
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.of(inviterWithRole(UserRole.OWNER)));
        when(clerkService.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ClerkUserCreationResult("clerk_new_id", true));

        RuntimeException dbFailure = new RuntimeException("db write failed");
        when(userRepository.save(any(User.class))).thenThrow(dbFailure);

        assertThatThrownBy(() -> service.inviteUser(requestFor(UserRole.STAFF)))
                .isSameAs(dbFailure);

        verify(clerkService).deleteUser("clerk_new_id");
        verifyNoInteractions(smsService);
    }

    // --- Missing authenticated user ---

    @Test
    void nullInviterUserId_throwsAccessDenied_beforeAnyClerkCall() {
        // Deliberately do not call TenantContext.setUserId(...) — simulates
        // TenantContext.getUserId() returning null, which (per the handoff
        // doc, §5) it does silently rather than throwing.
        assertThatThrownBy(() -> service.inviteUser(requestFor(UserRole.STAFF)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(clerkService);
        verifyNoInteractions(smsService);
        verifyNoInteractions(userRepository);
    }
}