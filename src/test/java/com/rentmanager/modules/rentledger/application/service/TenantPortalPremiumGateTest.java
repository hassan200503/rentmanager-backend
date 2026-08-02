package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.application.autopay.AutoPayService;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phases 2a/2b/3a/3b on the renter portal lease payload:
 * - manager contact exposed only when a MANAGER user actually exists;
 * - emergency contact exposed only when configured;
 * - branded colors are PREMIUM-gated (never leaked to COMMISSION
 *   landlords, and fail closed if a premium landlord's subscription
 *   lapses);
 * - billingMode/subscriptionStatus drive the real premium badge.
 */
class TenantPortalPremiumGateTest {

    private UserRepository userRepository;
    private TenantProfileRepository tenantProfileRepository;
    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentTransactionRepository rentTransactionRepository;
    private RentPaymentInitiationService rentPaymentInitiationService;
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    private AutoPayService autoPayService;

    private TenantPortalService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID landlordTenantId = UUID.randomUUID();
    private final String clerkUserId = "clerk_" + UUID.randomUUID();

    private TenantProfile tenantProfile;
    private Lease activeLease;
    private Unit unit;
    private Property property;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentTransactionRepository = mock(RentTransactionRepository.class);
        rentPaymentInitiationService = mock(RentPaymentInitiationService.class);
        rentPaymentRequestRepository = mock(RentPaymentRequestRepository.class);
        autoPayService = mock(AutoPayService.class);

        service = new TenantPortalService(
                userRepository, tenantProfileRepository, leaseRepository, unitRepository,
                propertyRepository, tenantRepository, rentLedgerEntryRepository,
                rentTransactionRepository, rentPaymentInitiationService,
                rentPaymentRequestRepository, autoPayService,
                mock(ReviewCommandService.class), mock(ReviewQueryService.class),
                mock(MaintenanceRequestCommandService.class), mock(MaintenanceRequestRepository.class),
                mock(AnnouncementQueryService.class));

        tenantProfile = buildTenantProfile();
        activeLease = buildActiveLease();
        unit = mockUnit();
        property = mockProperty();

        User renterUser = buildUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(renterUser));
        when(tenantProfileRepository.findByClerkUserId(clerkUserId))
                .thenReturn(Optional.of(tenantProfile));
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(activeLease));
        when(unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId))
                .thenReturn(Optional.of(unit));
        when(propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId))
                .thenReturn(Optional.of(property));
    }

    @Test
    void premiumActiveLandlordGetsBrandColorsAndBadgeFields() {
        Tenant landlord = buildLandlord(
                BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, "#123456", "#654321");
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        TenantLeaseResponse response = service.getLease(userId);

        assertEquals("#123456", response.landlordPrimaryColor());
        assertEquals("#654321", response.landlordSecondaryColor());
        assertEquals("PREMIUM_MONTHLY", response.billingMode());
        assertEquals("ACTIVE", response.subscriptionStatus());
        assertTrue(response.landlordVerified());
    }

    @Test
    void commissionLandlordNeverGetsBrandColors() {
        Tenant landlord = buildLandlord(
                BillingMode.COMMISSION, SubscriptionStatus.TRIAL, "#123456", "#654321");
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        TenantLeaseResponse response = service.getLease(userId);

        assertNull(response.landlordPrimaryColor());
        assertNull(response.landlordSecondaryColor());
        assertEquals("COMMISSION", response.billingMode());
        assertEquals("TRIAL", response.subscriptionStatus());
    }

    @Test
    void premiumColorsFailClosed_whenSubscriptionLapses() {
        Tenant landlord = buildLandlord(
                BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.LAPSED, "#123456", "#654321");
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        TenantLeaseResponse response = service.getLease(userId);

        assertNull(response.landlordPrimaryColor());
        assertNull(response.landlordSecondaryColor());
        assertFalse(response.landlordVerified());
    }

    @Test
    void premiumGracePeriodStillCountsAsPremium() {
        Tenant landlord = buildLandlord(
                BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.GRACE_PERIOD, "#123456", null);
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        TenantLeaseResponse response = service.getLease(userId);

        assertEquals("#123456", response.landlordPrimaryColor());
        assertNull(response.landlordSecondaryColor());
        assertTrue(response.landlordVerified());
    }

    @Test
    void managerContactExposed_whenManagerUserExists() {
        Tenant landlord = buildLandlord(BillingMode.COMMISSION, SubscriptionStatus.TRIAL, null, null);
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        User manager = User.rehydrate(
                UUID.randomUUID(), 0L, "clerk_manager", landlordTenantId,
                "manager@example.com", "Grace", "Muthoni", true, UserRole.MANAGER);
        when(userRepository.findByTenantIdAndRole(landlordTenantId, UserRole.MANAGER))
                .thenReturn(List.of(manager));

        TenantLeaseResponse response = service.getLease(userId);

        assertEquals("Grace Muthoni", response.managerName());
        assertEquals("manager@example.com", response.managerEmail());
    }

    @Test
    void managerContactNull_whenNoManagerUserExists() {
        Tenant landlord = buildLandlord(BillingMode.COMMISSION, SubscriptionStatus.TRIAL, null, null);
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));
        when(userRepository.findByTenantIdAndRole(landlordTenantId, UserRole.MANAGER))
                .thenReturn(List.of());

        TenantLeaseResponse response = service.getLease(userId);

        assertNull(response.managerName());
        assertNull(response.managerPhone());
        assertNull(response.managerEmail());
    }

    @Test
    void emergencyContactExposed_whenConfigured() {
        Tenant landlord = buildLandlord(BillingMode.COMMISSION, SubscriptionStatus.TRIAL, null, null);
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));
        when(landlord.getEmergencyContactPhone()).thenReturn("+254722111222");
        when(landlord.isEmergencyContact24h()).thenReturn(true);

        TenantLeaseResponse response = service.getLease(userId);

        assertEquals("+254722111222", response.emergencyContactPhone());
        assertTrue(response.emergencyContact24h());
    }

    @Test
    void emergencyContactHidden_whenNotConfigured() {
        Tenant landlord = buildLandlord(BillingMode.COMMISSION, SubscriptionStatus.TRIAL, null, null);
        when(tenantRepository.findById(landlordTenantId)).thenReturn(Optional.of(landlord));

        TenantLeaseResponse response = service.getLease(userId);

        assertNull(response.emergencyContactPhone());
        assertFalse(response.emergencyContact24h());
    }

    private User buildUser() {
        return User.rehydrate(
                userId, 0L, clerkUserId, landlordTenantId,
                "renter@test.com", "Test", "Renter", true, UserRole.STAFF);
    }

    private TenantProfile buildTenantProfile() {
        return TenantProfile.rehydrate(
                UUID.randomUUID(), landlordTenantId, clerkUserId,
                "Test Renter", "renter@test.com", "+254712345678", "12345678");
    }

    private Lease buildActiveLease() {
        Lease lease = Lease.create(
                landlordTenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantProfile.getId(),
                "LSE-GATE-TEST",
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusMonths(9),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                BigDecimal.ZERO,
                0,
                false);
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        return lease;
    }

    private Unit mockUnit() {
        Unit unit = mock(Unit.class);
        when(unit.getUnitNumber()).thenReturn("B2");
        when(unit.getLabel()).thenReturn("B2 - Two Bedroom");
        when(unit.getPropertyId()).thenReturn(UUID.randomUUID());
        return unit;
    }

    private Property mockProperty() {
        Property property = mock(Property.class);
        when(property.getName()).thenReturn("Sunrise Apartments");
        when(property.getAddress()).thenReturn(null);
        return property;
    }

    private Tenant buildLandlord(
            BillingMode billingMode, SubscriptionStatus status, String primary, String secondary) {
        Tenant landlord = mock(Tenant.class);
        when(landlord.getName()).thenReturn("Test Landlord");
        when(landlord.getPhoneNumber()).thenReturn("+254700000000");
        when(landlord.getEmail()).thenReturn("landlord@test.com");
        when(landlord.getTenantCode()).thenReturn("T-1234");
        when(landlord.getAddress()).thenReturn("Nairobi");
        when(landlord.getBillingMode()).thenReturn(billingMode);
        when(landlord.getSubscriptionStatus()).thenReturn(status);
        when(landlord.getEmergencyContactPhone()).thenReturn(null);
        when(landlord.isEmergencyContact24h()).thenReturn(false);
        when(landlord.getBrandingSettings()).thenReturn(
                BrandingSettings.of("logo.png", null, primary, secondary));
        return landlord;
    }
}
