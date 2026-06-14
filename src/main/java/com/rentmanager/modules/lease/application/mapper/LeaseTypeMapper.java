package com.rentmanager.modules.lease.application.mapper;

import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import com.rentmanager.modules.lease.domain.enums.LeaseType;

import java.util.Arrays;

public final class LeaseTypeMapper {

    private LeaseTypeMapper() {
        // prevent instantiation
    }

    // =========================================================
    // DTO → DOMAIN
    // =========================================================
    public static LeaseType toDomain(LeaseTypeDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("LeaseTypeDTO cannot be null");
        }

        return LeaseType.valueOf(dto.name());
    }

    // =========================================================
    // DOMAIN → DTO
    // =========================================================
    public static LeaseTypeDTO toDto(LeaseType domain) {
        if (domain == null) {
            throw new IllegalArgumentException("LeaseType cannot be null");
        }

        return LeaseTypeDTO.valueOf(domain.name());
    }

    // =========================================================
    // SAFE PARSE (for future API evolution)
    // =========================================================
    public static LeaseType safeToDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LeaseType value is required");
        }

        return Arrays.stream(LeaseType.values())
                .filter(e -> e.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid LeaseType: " + value)
                );
    }
}