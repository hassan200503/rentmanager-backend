package com.rentmanager.modules.lease.infrastructure.service;

import com.rentmanager.modules.lease.application.command.service.LeaseNumberGenerator;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates lease numbers in format: RM-2026-000041
 * Sequence is derived from total lease count in DB — simple and collision-safe
 * for single-node deployments. Swap for a DB sequence if you go multi-node.
 */
@Component
@RequiredArgsConstructor
public class DefaultLeaseNumberGenerator implements LeaseNumberGenerator {

    private final LeaseRepository leaseRepository;

    @Override
    public String generate(UUID propertyId, UUID unitId) {
        int year = LocalDate.now().getYear();
        long count = leaseRepository.countAll() + 1;
        return String.format("RM-%d-%06d", year, count);
    }
}