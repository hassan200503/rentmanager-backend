package com.rentmanager.modules.maintenance.application;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.application.service.LandlordMaintenanceSubmissionGuard;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cross-organisation ids on POST /api/v1/maintenance are refused. */
class LandlordMaintenanceSubmissionGuardTest {

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    private UnitRepository units;
    private PropertyRepository properties;
    private TenantProfileRepository profiles;
    private LeaseRepository leases;
    private LandlordMaintenanceSubmissionGuard guard;

    @BeforeEach
    void setUp() {
        units = mock(UnitRepository.class);
        properties = mock(PropertyRepository.class);
        profiles = mock(TenantProfileRepository.class);
        leases = mock(LeaseRepository.class);
        guard = new LandlordMaintenanceSubmissionGuard(units, properties, profiles, leases);
    }

    private void stubOwnUnitAndProperty() {
        Unit unit = mock(Unit.class);
        when(unit.getPropertyId()).thenReturn(propertyId);
        when(units.findByIdAndTenantId(unitId, orgA)).thenReturn(Optional.of(unit));
        when(properties.findByIdAndTenantId(propertyId, orgA)).thenReturn(Optional.of(mock(Property.class)));
    }

    private TenantProfile profileIn(UUID org) {
        return TenantProfile.rehydrate(UUID.randomUUID(), org, "user_x", "Renter", "r@x", "+254712345678", "1");
    }

    @Test
    void allIdsInTheCallersOrganisationPass() {
        stubOwnUnitAndProperty();
        TenantProfile renter = profileIn(orgA);
        when(profiles.findById(renter.getId())).thenReturn(Optional.of(renter));
        Lease lease = Lease.restore(UUID.randomUUID(), orgA, propertyId, unitId, renter.getId(), "L-1",
                LeaseType.FIXED_TERM, BillingCycle.MONTHLY, LocalDate.now(), LocalDate.now().plusYears(1),
                new BigDecimal("1000"), new BigDecimal("1000"), LeaseStatus.ACTIVE);
        when(leases.findByIdAndTenantId(lease.getId(), orgA)).thenReturn(Optional.of(lease));

        assertThatCode(() -> guard.verify(orgA, unitId, propertyId, renter.getId(), lease.getId())).doesNotThrowAnyException();
    }

    @Test
    void aRenterFromAnotherOrganisationIsRefused() {
        stubOwnUnitAndProperty();
        TenantProfile foreign = profileIn(orgB);
        when(profiles.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> guard.verify(orgA, unitId, propertyId, foreign.getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aUnitFromAnotherOrganisationIsRefused() {
        when(units.findByIdAndTenantId(unitId, orgA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.verify(orgA, unitId, propertyId, UUID.randomUUID(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aUnitThatIsNotInTheNamedPropertyIsRefused() {
        Unit unit = mock(Unit.class);
        when(unit.getPropertyId()).thenReturn(UUID.randomUUID());
        when(units.findByIdAndTenantId(unitId, orgA)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> guard.verify(orgA, unitId, propertyId, UUID.randomUUID(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aLeaseForADifferentRenterIsRefused() {
        stubOwnUnitAndProperty();
        TenantProfile renter = profileIn(orgA);
        when(profiles.findById(renter.getId())).thenReturn(Optional.of(renter));
        Lease someoneElses = Lease.restore(UUID.randomUUID(), orgA, propertyId, unitId, UUID.randomUUID(), "L-2",
                LeaseType.FIXED_TERM, BillingCycle.MONTHLY, LocalDate.now(), LocalDate.now().plusYears(1),
                new BigDecimal("1000"), new BigDecimal("1000"), LeaseStatus.ACTIVE);
        when(leases.findByIdAndTenantId(someoneElses.getId(), orgA)).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> guard.verify(orgA, unitId, propertyId, renter.getId(), someoneElses.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
