package com.rentmanager.modules.maintenance.application.service;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Validates a landlord-side maintenance submission before it is created.
 *
 * POST /api/v1/maintenance takes unit, property, renter profile and lease ids
 * from the request body (a caretaker logging a problem a renter reported in
 * person). Nothing checked those ids, so a member of one landlord organisation
 * could create a request pointing at another organisation's unit or renter —
 * and the request list, which enriches rows with the renter's name, would then
 * show that other organisation's renter to them.
 *
 * Every id must belong to the caller's organisation and they must agree with
 * each other: the unit is in the property, the lease (when given) is for that
 * unit and that renter. Any mismatch is "not found", so the endpoint cannot be
 * used to probe which ids exist elsewhere.
 */
@Component
@RequiredArgsConstructor
public class LandlordMaintenanceSubmissionGuard {

    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;

    @Transactional(readOnly = true)
    public void verify(UUID tenantId, UUID unitId, UUID propertyId, UUID tenantProfileId, UUID leaseId) {
        if (tenantId == null) {
            throw notFound();
        }
        Unit unit = unitRepository.findByIdAndTenantId(unitId, tenantId).orElseThrow(LandlordMaintenanceSubmissionGuard::notFound);
        if (!Objects.equals(unit.getPropertyId(), propertyId)) {
            throw notFound();
        }
        propertyRepository.findByIdAndTenantId(propertyId, tenantId).orElseThrow(LandlordMaintenanceSubmissionGuard::notFound);

        TenantProfile profile = tenantProfileRepository.findById(tenantProfileId).orElseThrow(LandlordMaintenanceSubmissionGuard::notFound);
        if (!Objects.equals(profile.getTenantId(), tenantId)) {
            throw notFound();
        }

        if (leaseId != null) {
            Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId).orElseThrow(LandlordMaintenanceSubmissionGuard::notFound);
            if (!Objects.equals(lease.getUnitId(), unitId) || !Objects.equals(lease.getTenantProfileId(), tenantProfileId)) {
                throw notFound();
            }
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Unit, renter or lease not found", ErrorCode.RESOURCE_NOT_FOUND);
    }
}
