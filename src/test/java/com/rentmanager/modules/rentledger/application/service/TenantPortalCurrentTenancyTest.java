package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.application.autopay.AutoPayService;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.review.application.RenterReviewQueryService;
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regressions for two renter-portal defects found while building the mobile app:
 * <ol>
 *   <li>A renter with profiles under two landlords (they moved) made the
 *       single-result profile lookup throw, taking the whole portal down.</li>
 *   <li>Only ACTIVE counted as a current lease, but RENEWED leases are billed
 *       too — so a renter who renewed was charged and could not pay.</li>
 * </ol>
 * Manual mocks per CLAUDE.md conventions.
 */
class TenantPortalCurrentTenancyTest {

    private static final String CLERK = "user_renter";
    private static final UUID USER_ID = UUID.randomUUID();

    private TenantProfileRepository profiles;
    private LeaseRepository leases;
    private RentLedgerEntryRepository entries;
    private RentPaymentInitiationService initiation;
    private TenantPortalService service;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        profiles = mock(TenantProfileRepository.class);
        leases = mock(LeaseRepository.class);
        entries = mock(RentLedgerEntryRepository.class);
        initiation = mock(RentPaymentInitiationService.class);
        service = new TenantPortalService(
                users, profiles, leases, mock(UnitRepository.class), mock(PropertyRepository.class),
                mock(PropertyMediaRepository.class), mock(TenantRepository.class), entries,
                mock(RentTransactionRepository.class), initiation, mock(RentPaymentRequestRepository.class),
                mock(AutoPayService.class), mock(ReviewCommandService.class), mock(ReviewQueryService.class),
                mock(RenterReviewQueryService.class), mock(MaintenanceRequestCommandService.class),
                mock(MaintenanceRequestRepository.class), mock(AnnouncementQueryService.class),
                mock(StkPushRateLimiter.class), mock(DepositRepository.class));

        when(users.findById(USER_ID)).thenReturn(Optional.of(
                User.rehydrate(USER_ID, 0L, CLERK, null, "renter@test", "R", "T", true, null)));
    }

    private TenantProfile profile(UUID landlord) {
        return TenantProfile.rehydrate(UUID.randomUUID(), landlord, CLERK, "Renter", "r@test", "+254712345678", "1");
    }

    private Lease lease(UUID landlord, UUID profileId, LeaseStatus status, LocalDate start) {
        return Lease.restore(UUID.randomUUID(), landlord, UUID.randomUUID(), UUID.randomUUID(), profileId,
                "LSE-" + UUID.randomUUID(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                start, start.plusYears(1), new BigDecimal("20000"), new BigDecimal("20000"), status);
    }

    private RentLedgerEntry dueEntry(UUID landlord, UUID leaseId, UUID entryId) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(landlord);
        when(entry.getLeaseId()).thenReturn(leaseId);
        return entry;
    }

    @Test
    void renterWhoMovedBetweenLandlordsPaysAtTheirCurrentHome() {
        UUID oldLandlord = UUID.randomUUID();
        UUID newLandlord = UUID.randomUUID();
        TenantProfile oldProfile = profile(oldLandlord);
        TenantProfile newProfile = profile(newLandlord);
        Lease ended = lease(oldLandlord, oldProfile.getId(), LeaseStatus.TERMINATED, LocalDate.now().minusYears(2));
        Lease current = lease(newLandlord, newProfile.getId(), LeaseStatus.ACTIVE, LocalDate.now().minusMonths(2));
        UUID entryId = UUID.randomUUID();
        RentLedgerEntry entry = dueEntry(newLandlord, current.getId(), entryId);

        when(profiles.findAllByClerkUserId(CLERK)).thenReturn(List.of(oldProfile, newProfile));
        when(leases.findAllByTenantAndTenantProfile(oldLandlord, oldProfile.getId())).thenReturn(List.of(ended));
        when(leases.findAllByTenantAndTenantProfile(newLandlord, newProfile.getId())).thenReturn(List.of(current));
        when(entries.findByIdAndTenantId(entryId, newLandlord)).thenReturn(Optional.of(entry));
        when(initiation.initiate(any(), any(), any())).thenReturn(mock(RentPaymentRequest.class));

        assertThatCode(() -> service.initiateRentPayment(USER_ID, entryId, "0712345678")).doesNotThrowAnyException();
        verify(initiation).initiate(eq(newLandlord), eq(entryId), eq("+254712345678"));
    }

    @Test
    void renterWithARenewedLeaseCanStillPay() {
        UUID landlord = UUID.randomUUID();
        TenantProfile p = profile(landlord);
        Lease renewed = lease(landlord, p.getId(), LeaseStatus.RENEWED, LocalDate.now().minusMonths(1));
        UUID entryId = UUID.randomUUID();
        RentLedgerEntry entry = dueEntry(landlord, renewed.getId(), entryId);

        when(profiles.findAllByClerkUserId(CLERK)).thenReturn(List.of(p));
        when(leases.findAllByTenantAndTenantProfile(landlord, p.getId())).thenReturn(List.of(renewed));
        when(entries.findByIdAndTenantId(entryId, landlord)).thenReturn(Optional.of(entry));
        when(initiation.initiate(any(), any(), any())).thenReturn(mock(RentPaymentRequest.class));

        assertThatCode(() -> service.initiateRentPayment(USER_ID, entryId, "0112345678")).doesNotThrowAnyException();
        verify(initiation).initiate(eq(landlord), eq(entryId), eq("+254112345678"));
    }
}
