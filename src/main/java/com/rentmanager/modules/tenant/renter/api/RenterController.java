package com.rentmanager.modules.tenant.renter.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.renter.application.RenterDirectoryService;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The landlord's renters.
 *
 * The organisation is always {@code TenantContext.getTenantId()}, derived from
 * the verified Clerk JWT — never from a parameter or body, so no landlord can
 * read or add renters under another's account.
 *
 * Roles: recording a tenancy is OWNER/MANAGER work. STAFF (the caretaker who
 * collects cash) can read the list, because they need to know who lives where,
 * but cannot create records.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/renters")
public class RenterController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RenterDirectoryService renterDirectoryService;

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<RenterResponse>> add(@Valid @RequestBody AddRenterRequest request) {
        UUID landlordTenantId = TenantContext.getTenantId();

        RenterResponse response = RenterResponse.from(renterDirectoryService.add(
                landlordTenantId,
                request.fullName(),
                request.phone(),
                request.email(),
                request.nationalId()
        ));

        return ResponseEntity.ok(ApiResponse.ok("Renter saved", response));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<RenterResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID landlordTenantId = TenantContext.getTenantId();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<RenterResponse> response = renterDirectoryService
                .list(landlordTenantId, PageRequest.of(Math.max(page, 0), safeSize))
                .map(RenterResponse::from);

        return ResponseEntity.ok(ApiResponse.ok("Renters retrieved", response));
    }

    /** Backs the renter picker on the lease form. */
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RenterResponse>>> search(@RequestParam String query) {
        UUID landlordTenantId = TenantContext.getTenantId();

        List<RenterResponse> response = renterDirectoryService.search(landlordTenantId, query)
                .stream()
                .map(RenterResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Renters retrieved", response));
    }
}
