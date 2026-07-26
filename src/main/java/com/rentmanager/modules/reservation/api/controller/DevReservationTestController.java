package com.rentmanager.modules.reservation.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.identity.clerk.SignInTokenResult;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/v1/dev/reservations")
public class DevReservationTestController {

    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final ClerkService clerkService;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final TenantRepository tenantRepository;

    @PostMapping("/setup-test-tenant")
    public ResponseEntity<ApiResponse<DevSetupResponse>> setupTestTenant(
            @RequestBody DevSetupRequest request
    ) {
        // 1. Create a landlord tenant
        Tenant landlord = Tenant.create(
                "DEV-" + System.currentTimeMillis(),
                "Dev Landlord",
                "dev-landlord",
                "landlord@dev.local",
                "+254700000000",
                TenantType.STANDARD
        );
        tenantRepository.save(landlord);
        UUID landlordTenantId = landlord.getId();
        Property property = Property.create(
                landlordTenantId,
                request.propertyName != null ? request.propertyName : "Test Property",
                PropertyType.APARTMENT,
                null, null, null,
                "Dev test property",
                "dev-setup-" + UUID.randomUUID()
        );
        propertyRepository.save(property);
        UUID propertyId = property.getId();
        Unit unit = Unit.rehydrate(
                UUID.randomUUID(),
                landlordTenantId,
                propertyId,
                "DEV-001",
                "Dev Test Unit",
                null,
                UnitStatus.ACTIVE,
                UnitOccupancyStatus.VACANT,
                BigDecimal.valueOf(15000),
                null,
                "Dev test unit for portal verification",
                null,
                null
        );
        unitRepository.save(unit);

        // 3. Create Clerk user + sign-in token
        ClerkUserCreationResult clerkResult = clerkService.createTenantUser(
                request.fullName != null ? request.fullName : "Test Renter",
                request.email,
                request.phone
        );
        String clerkUserId = clerkResult.clerkUserId();

        SignInTokenResult tokenResult = clerkService.createSignInToken(
                clerkUserId,
                604800
        );

        // 4. Create TenantProfile
        TenantProfile profile = TenantProfile.create(
                landlordTenantId,
                clerkUserId,
                request.fullName != null ? request.fullName : "Test Renter",
                request.email,
                request.phone,
                request.nationalId != null ? request.nationalId : "DEV123456",
                "dev-setup-" + UUID.randomUUID()
        );
        tenantProfileRepository.save(profile);

        // 5. Create a pending-activation lease
        Lease lease = Lease.createPendingActivation(
                landlordTenantId,
                propertyId,
                unit.getId(),
                profile.getId(),
                "LSE-DEV-" + System.currentTimeMillis(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                unit.getRentAmount(),
                BigDecimal.valueOf(15000),
                null, null, false
        );
        leaseRepository.save(lease);

        log.info("Dev test tenant setup complete. clerkUserId={}, signInUrl={}",
                clerkUserId, tokenResult.url());

        return ResponseEntity.ok(ApiResponse.ok(
                "Test tenant created",
                new DevSetupResponse(
                        tokenResult.url(),
                        tokenResult.tokenId(),
                        clerkUserId,
                        profile.getId(),
                        unit.getId(),
                        propertyId,
                        landlordTenantId
                )
        ));
    }

    private record DevSetupResponse(
            String signInUrl,
            String signInTokenId,
            String clerkUserId,
            UUID tenantProfileId,
            UUID unitId,
            UUID propertyId,
            UUID landlordTenantId
    ) {}

    private record DevSetupRequest(
            String fullName,
            String email,
            String phone,
            String nationalId,
            String propertyName
    ) {}
}
