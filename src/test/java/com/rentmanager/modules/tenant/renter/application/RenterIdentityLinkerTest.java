package com.rentmanager.modules.tenant.renter.application;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Claiming a landlord-entered tenancy for the person it belongs to.
 *
 * The security line: only UNLINKED records are ever claimed, and only on the
 * email Clerk verified. Matching on a landlord-typed phone number would let
 * someone take over another person's rent history by entering their number.
 */
class RenterIdentityLinkerTest {

    private TenantProfileRepository repository;
    private RenterIdentityLinker linker;

    @BeforeEach
    void setUp() {
        repository = mock(TenantProfileRepository.class);
        linker = new RenterIdentityLinker(repository);
    }

    private TenantProfile unlinked(String email) {
        return TenantProfile.createForLandlord(UUID.randomUUID(), "Amina Wanjiru", "+254722123456", email, null, "t");
    }

    @Test
    void claimsEveryUnlinkedRecordWithThatEmail_becauseOnePersonMayRentFromSeveralLandlords() {
        TenantProfile first = unlinked("amina@example.com");
        TenantProfile second = unlinked("amina@example.com");
        when(repository.findUnlinkedByEmail("amina@example.com")).thenReturn(List.of(first, second));
        when(repository.save(any(TenantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TenantProfile> linked = linker.linkByVerifiedEmail("user_amina", "amina@example.com");

        assertThat(linked).hasSize(2);
        assertThat(linked).allSatisfy(p -> {
            assertThat(p.getClerkUserId()).isEqualTo("user_amina");
            assertThat(p.isUnlinked()).isFalse();
        });
    }

    @Test
    void doesNothingWhenNoRecordMatches() {
        when(repository.findUnlinkedByEmail("nobody@example.com")).thenReturn(List.of());

        assertThat(linker.linkByVerifiedEmail("user_x", "nobody@example.com")).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void doesNothingWithoutAnIdentityOrAnEmail() {
        assertThat(linker.linkByVerifiedEmail(null, "amina@example.com")).isEmpty();
        assertThat(linker.linkByVerifiedEmail("user_x", null)).isEmpty();
        assertThat(linker.linkByVerifiedEmail("user_x", "  ")).isEmpty();
        assertThat(linker.linkByVerifiedEmail("  ", "amina@example.com")).isEmpty();
        verify(repository, never()).findUnlinkedByEmail(any());
        verify(repository, never()).save(any());
    }

    @Test
    void aRecordAlreadyHeldByAnotherIdentityCannotBeReClaimed() {
        // The repository only returns unlinked rows, so this can only happen if
        // that guarantee ever breaks. The domain refuses regardless.
        TenantProfile taken = unlinked("amina@example.com");
        taken.linkIdentity("user_first");

        assertThatThrownBy(() -> taken.linkIdentity("user_second"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(taken.getClerkUserId()).isEqualTo("user_first");
    }

    @Test
    void relinkingTheSameIdentityIsHarmless() {
        TenantProfile profile = unlinked("amina@example.com");
        profile.linkIdentity("user_amina");
        profile.linkIdentity("user_amina");

        assertThat(profile.getClerkUserId()).isEqualTo("user_amina");
    }

    @Test
    void anErasedRenterHoldsATombstoneAndIsNeverUnlinkedAgain() {
        // Account deletion sets a tombstone rather than null, so an erased
        // renter cannot be re-claimed by anyone signing up with that email.
        TenantProfile profile = unlinked("amina@example.com");
        profile.linkIdentity("user_amina");
        profile.unlinkIdentity("tombstone:" + profile.getId());

        assertThat(profile.isUnlinked()).isFalse();
    }
}
