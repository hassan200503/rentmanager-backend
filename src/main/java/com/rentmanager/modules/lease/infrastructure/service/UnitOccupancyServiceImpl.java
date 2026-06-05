package com.rentmanager.modules.lease.infrastructure.service;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UnitOccupancyServiceImpl implements UnitOccupancyService {

    private final LeaseRepository leaseRepository;

    public UnitOccupancyServiceImpl(LeaseRepository leaseRepository) {
        this.leaseRepository = leaseRepository;
    }

    @Override
    public boolean isUnitOccupied(UUID unitId) {
        return leaseRepository.findByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public void validateUnitAvailability(UUID unitId) {
        if (isUnitOccupied(unitId)) {
            throw new BusinessException(
                    "Unit already has an active lease",
                    ErrorCode.UNIT_OCCUPIED
            );
        }
    }
}