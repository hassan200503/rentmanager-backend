package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.query.service.PublicUnitQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/units")
public class PublicUnitQueryController {

    private final PublicUnitQueryService publicUnitQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicUnitResponse>>> getVacantUnits(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Vacant units retrieved successfully",
                publicUnitQueryService.getVacantUnits(keyword, pageable)
        ));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<ApiResponse<Page<PublicUnitResponse>>> getVacantUnitsByProperty(
            @PathVariable UUID propertyId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Property vacant units retrieved successfully",
                publicUnitQueryService.getVacantUnitsByProperty(propertyId, pageable)
        ));
    }

    @GetMapping("/{unitId}")
    public ResponseEntity<ApiResponse<PublicUnitResponse>> getVacantUnit(
            @PathVariable UUID unitId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Unit retrieved successfully",
                publicUnitQueryService.getVacantUnitById(unitId)
        ));
    }
}