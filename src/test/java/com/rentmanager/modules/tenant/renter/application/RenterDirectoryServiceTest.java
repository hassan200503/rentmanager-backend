package com.rentmanager.modules.tenant.renter.application;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A landlord recording the tenants already living in their units — the case
 * the product could not do at all before: the only code that created a renter
 * was the public reservation saga.
 */
class RenterDirectoryServiceTest {

    private TenantProfileRepository repository;
    private RenterDirectoryService service;
    private UUID landlord;

    @BeforeEach
    void setUp() {
        repository = mock(TenantProfileRepository.class);
        service = new RenterDirectoryService(repository);
        landlord = UUID.randomUUID();
    }

    @Test
    void recordsARenterWithTheirPhoneNormalisedToE164() {
        when(repository.findUnlinkedByTenantIdAndPhone(eq(landlord), any())).thenReturn(Optional.empty());
        when(repository.save(any(TenantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantProfile saved = service.add(landlord, "  Amina Wanjiru ", "0722123456", null, null);

        assertThat(saved.getTenantId()).isEqualTo(landlord);
        assertThat(saved.getFullName()).isEqualTo("Amina Wanjiru");
        assertThat(saved.getPhone()).isEqualTo("+254722123456");
        assertThat(saved.getEmail()).isNull();
        // No account yet: this tenancy is claimed the first time that person
        // signs in with a matching verified email.
        assertThat(saved.isUnlinked()).isTrue();
    }

    @Test
    void acceptsTheNewerZeroOneNumbers() {
        when(repository.findUnlinkedByTenantIdAndPhone(eq(landlord), any())).thenReturn(Optional.empty());
        when(repository.save(any(TenantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.add(landlord, "Brian Otieno", "0110000000", null, null).getPhone())
                .isEqualTo("+254110000000");
    }

    @Test
    void addingTheSamePhoneTwiceReturnsTheSameRecordInsteadOfSplittingRentHistory() {
        TenantProfile existing = TenantProfile.createForLandlord(
                landlord, "Amina Wanjiru", "+254722123456", null, null, "test");
        when(repository.findUnlinkedByTenantIdAndPhone(landlord, "+254722123456"))
                .thenReturn(Optional.of(existing));

        TenantProfile result = service.add(landlord, "Amina W.", "+254722123456", null, null);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void refusesAPhoneNumberSafaricomWouldNotAccept() {
        assertThatThrownBy(() -> service.add(landlord, "Amina", "12345", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(landlord, "Amina", null, null, null))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesAMissingName() {
        assertThatThrownBy(() -> service.add(landlord, "   ", "0722123456", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refusesToRecordARenterWithNoOrganisationInContext() {
        assertThatThrownBy(() -> service.add(null, "Amina", "0722123456", null, null))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void searchIgnoresAnEmptyQueryRatherThanListingEveryRenter() {
        assertThat(service.search(landlord, "  ")).isEmpty();
        assertThat(service.search(landlord, null)).isEmpty();
        verify(repository, never()).searchByNameOrPhone(any(), any());
    }
}
