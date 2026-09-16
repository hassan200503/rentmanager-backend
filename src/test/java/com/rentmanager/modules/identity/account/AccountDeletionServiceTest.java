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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {

    private static final String CLERK = "user_abc";

    private ClerkService clerk;
    private UserRepository users;
    private TenantProfileRepository profiles;
    private PushDeviceService devices;
    private NotificationPreferenceService preferences;
    private AccountDeletionRequestStore requests;
    private AccountDeletionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        clerk = mock(ClerkService.class);
        users = mock(UserRepository.class);
        profiles = mock(TenantProfileRepository.class);
        devices = mock(PushDeviceService.class);
        preferences = mock(NotificationPreferenceService.class);
        requests = mock(AccountDeletionRequestStore.class);

        ObjectProvider<ClerkService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clerk);

        // Run the transactional callback inline.
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            Consumer<org.springframework.transaction.TransactionStatus> body = inv.getArgument(0);
            body.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());

        service = new AccountDeletionService(provider, users, profiles, devices, preferences, requests, tx);
    }

    private User user(UserRole role, UUID tenantId) {
        return User.rehydrate(UUID.randomUUID(), 0L, CLERK, tenantId, "person@test.co.ke", "Amina", "W", true, role);
    }

    @Test
    void ownerOfAnOrganisationIsRecordedForReviewAndNothingIsDeleted() {
        when(users.findByClerkUserId(CLERK)).thenReturn(Optional.of(user(UserRole.OWNER, UUID.randomUUID())));

        AccountDeletionService.Result result = service.requestDeletion(CLERK);

        assertThat(result.outcome()).isEqualTo(AccountDeletionService.Outcome.PENDING_REVIEW);
        verify(requests).record(eq(CLERK), eq("APP"), eq("PENDING_REVIEW"), anyString());
        verifyNoInteractions(clerk, devices, preferences, profiles);
    }

    @Test
    void clerkRefusalChangesNothingLocallyAndAsksToRetry() {
        when(users.findByClerkUserId(CLERK)).thenReturn(Optional.of(user(null, null)));
        doThrow(new RuntimeException("clerk 500")).when(clerk).deleteUserStrict(CLERK);

        assertThatThrownBy(() -> service.requestDeletion(CLERK))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nothing has been changed");

        verify(requests).record(eq(CLERK), eq("APP"), eq("FAILED"), anyString());
        verifyNoInteractions(devices, preferences, profiles);
        verify(users, never()).save(any());
    }

    @Test
    void renterDeletionRemovesLoginDevicesPreferencesAndIdentityButKeepsLandlordRecords() {
        User renter = user(null, null);
        TenantProfile atLandlordA = TenantProfile.rehydrate(UUID.randomUUID(), UUID.randomUUID(), CLERK,
                "Amina W", "amina@test.co.ke", "+254712345678", "1234");
        TenantProfile atLandlordB = TenantProfile.rehydrate(UUID.randomUUID(), UUID.randomUUID(), CLERK,
                "Amina W", "amina@test.co.ke", "+254712345678", "1234");
        when(users.findByClerkUserId(CLERK)).thenReturn(Optional.of(renter));
        when(profiles.findAllByClerkUserId(CLERK)).thenReturn(List.of(atLandlordA, atLandlordB));

        AccountDeletionService.Result result = service.requestDeletion(CLERK);

        assertThat(result.outcome()).isEqualTo(AccountDeletionService.Outcome.COMPLETED);
        verify(clerk).deleteUserStrict(CLERK);
        verify(devices).revokeAllFor(CLERK);
        verify(preferences).deleteAll(CLERK);

        // Login identity erased from the account row.
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getClerkUserId()).startsWith("deleted:");
        assertThat(saved.getValue().getEmail()).endsWith("@deleted.invalid").doesNotContain("person@test");
        assertThat(saved.getValue().getFirstName()).isNull();
        assertThat(saved.getValue().isActive()).isFalse();

        // Both renter profiles detached with distinct tombstones; landlord's record kept.
        verify(profiles, times(2)).save(any());
        assertThat(atLandlordA.getClerkUserId()).startsWith("deleted:").isNotEqualTo(atLandlordB.getClerkUserId());
        assertThat(atLandlordA.getFullName()).isEqualTo("Amina W");
        assertThat(atLandlordA.isWhatsAppOptIn()).isFalse();

        verify(requests).record(eq(CLERK), eq("APP"), eq("COMPLETED"), anyString());
    }

    @Test
    void staffMemberCanDeleteTheirOwnAccount() {
        when(users.findByClerkUserId(CLERK)).thenReturn(Optional.of(user(UserRole.STAFF, UUID.randomUUID())));
        when(profiles.findAllByClerkUserId(CLERK)).thenReturn(List.of());

        assertThat(service.requestDeletion(CLERK).outcome()).isEqualTo(AccountDeletionService.Outcome.COMPLETED);
        verify(clerk).deleteUserStrict(CLERK);
    }
}
