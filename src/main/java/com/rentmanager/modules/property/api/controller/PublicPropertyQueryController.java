package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.query.service.PublicPropertyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/properties")
public class PublicPropertyQueryController {

    private final PublicPropertyQueryService publicPropertyQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicPropertyResponse>>> getProperties(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Properties retrieved successfully",
                        publicPropertyQueryService.getProperties(keyword, pageable)
                )
        );
    }

    @GetMapping("/{propertyId}")
    public ResponseEntity<ApiResponse<PublicPropertyResponse>> getProperty(
            @PathVariable UUID propertyId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Property retrieved successfully",
                        publicPropertyQueryService.getProperty(propertyId)
                )
        );
    }
}