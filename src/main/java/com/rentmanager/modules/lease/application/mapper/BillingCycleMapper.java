package com.rentmanager.modules.lease.application.mapper;

import com.rentmanager.contract.lease.dto.BillingCycleDTO;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;

import java.util.Arrays;

public final class BillingCycleMapper {

    private BillingCycleMapper() {
        // prevent instantiation
    }

    // =========================================================
    // DTO → DOMAIN
    // =========================================================
    public static BillingCycle toDomain(BillingCycleDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("BillingCycleDTO cannot be null");
        }

        return BillingCycle.valueOf(dto.name());
    }

    // =========================================================
    // DOMAIN → DTO
    // =========================================================
    public static BillingCycleDTO toDto(BillingCycle domain) {
        if (domain == null) {
            throw new IllegalArgumentException("BillingCycle cannot be null");
        }

        return BillingCycleDTO.valueOf(domain.name());
    }

    // =========================================================
    // SAFE PARSE (future-proofing for API evolution)
    // =========================================================
    public static BillingCycle safeToDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BillingCycle value is required");
        }

        return Arrays.stream(BillingCycle.values())
                .filter(e -> e.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid BillingCycle: " + value)
                );
    }
}