package com.rentmanager.modules.reservation.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.SignInTokenResult;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenant-portal/dev")
public class DevTenantPortalSetupController {

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final LeaseRepository leaseRepository;
    private final ClerkService clerkService;

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<DevSetupResponse>> setup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "leaseNumber", required = false) String leaseNumber
    ) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    "You must be a landlord (have a tenant_id claim) to use this endpoint. " +
                    "Sign in with your landlord Clerk account first.",
                    "DEV_SETUP_FAILED"
            ));
        }

        var userEntity = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String clerkUserId = userEntity.getClerkUserId();
        String email = userEntity.getEmail();

        // ── Real data path: look up existing lease by number ──────────────
        if (leaseNumber != null && !leaseNumber.isBlank()) {
            // Remove any existing dev TenantProfile linked to this Clerk user
            // to avoid unique constraint violation on (tenant_id, clerk_user_id)
            tenantProfileRepository.findByTenantIdAndClerkUserId(tenantId, clerkUserId)
                    .ifPresent(devProfile -> {
                        tenantProfileRepository.deleteById(devProfile.getId());
                        log.info("Removed dev TenantProfile {} to free up clerkUserId for real profile",
                                devProfile.getId());
                    });

            Lease realLease = leaseRepository.findAllByTenant(tenantId).stream()
                    .filter(l -> leaseNumber.equals(l.getLeaseNumber()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Lease not found: " + leaseNumber));

            TenantProfile realProfile = tenantProfileRepository
                    .findById(realLease.getTenantProfileId())
                    .orElseThrow(() -> new IllegalStateException(
                            "TenantProfile not found for lease " + leaseNumber));

            // Re-link the real TenantProfile to the landlord's Clerk user
            // so resolveTenantProfile() finds it when using the landlord's session
            log.info("Relinking TenantProfile {} (originally for '{}') to landlord clerkUserId '{}'",
                    realProfile.getId(), realProfile.getFullName(), clerkUserId);

            tenantProfileRepository.save(TenantProfile.rehydrate(
                    realProfile.getId(),
                    realProfile.getTenantId(),
                    clerkUserId,
                    realProfile.getFullName(),
                    realProfile.getEmail(),
                    realProfile.getPhone(),
                    realProfile.getNationalId()
            ));

            return ResponseEntity.ok(ApiResponse.ok(
                    "Linked real renter profile '" + realProfile.getFullName() +
                    "' to your account. Navigate to /portal in dev renter mode to preview.",
                    new DevSetupResponse(
                            null,
                            realProfile.getId(),
                            clerkUserId,
                            user.getTenantId().toString(),
                            realLease.getPropertyId(),
                            realLease.getUnitId(),
                            realLease.getLeaseNumber(),
                            realProfile.getFullName()
                    )
            ));
        }

        // ── Fake data path: create everything from scratch ────────────────
        TenantProfile existingProfile = tenantProfileRepository
                .findByTenantIdAndClerkUserId(tenantId, clerkUserId)
                .orElse(null);

        UUID tenantProfileId;
        if (existingProfile != null) {
            tenantProfileId = existingProfile.getId();
            log.info("Reusing existing TenantProfile {}", tenantProfileId);
        } else {
            TenantProfile profile = TenantProfile.create(
                    tenantId,
                    clerkUserId,
                    "Dev Renter (" + email + ")",
                    email,
                    "+254700000001",
                    "DEV123456",
                    "dev-setup-" + UUID.randomUUID()
            );
            tenantProfileRepository.save(profile);
            tenantProfileId = profile.getId();
            log.info("Created TenantProfile {}", tenantProfileId);
        }

        var property = propertyRepository.findAllByTenantId(tenantId, org.springframework.data.domain.Pageable.ofSize(1))
                .stream().findFirst().orElseGet(() -> {
                    Property p = Property.create(
                            tenantId,
                            "DEV Test Property",
                            PropertyType.APARTMENT,
                            null, null, null,
                            "Auto-created dev test property",
                            "dev-setup-" + UUID.randomUUID()
                    );
                    return propertyRepository.save(p);
                });

        var unit = unitRepository.findByTenantIdAndPropertyId(tenantId, property.getId(),
                        org.springframework.data.domain.Pageable.ofSize(1))
                .stream().findFirst().orElseGet(() -> {
                    Unit u = Unit.rehydrate(
                            UUID.randomUUID(),
                            tenantId,
                            property.getId(),
                            "DEV-001",
                            "Dev Test Unit",
                            null,
                            UnitStatus.ACTIVE,
                            UnitOccupancyStatus.VACANT,
                            BigDecimal.valueOf(15000),
                            null,
                            "Auto-created dev test unit",
                            null,
                            null
                    );
                    return unitRepository.save(u);
                });

        String devLeaseNumber = "LSE-DEV-" + System.currentTimeMillis();
        Lease lease = Lease.createPendingActivation(
                tenantId,
                property.getId(),
                unit.getId(),
                tenantProfileId,
                devLeaseNumber,
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(15000),
                null, null, false
        );
        lease.activatePending();
        leaseRepository.save(lease);

        SignInTokenResult tokenResult = clerkService.createSignInToken(clerkUserId, 604800);

        log.info("Dev tenant portal setup complete. clerkUserId={}, profileId={}, signInUrl={}",
                clerkUserId, tenantProfileId, tokenResult.url());

        return ResponseEntity.ok(ApiResponse.ok(
                "Tenant portal profile created. Click the sign-in link below or navigate to /portal in dev renter mode.",
                new DevSetupResponse(
                        tokenResult.url(),
                        tenantProfileId,
                        clerkUserId,
                        user.getTenantId().toString(),
                        property.getId(),
                        unit.getId(),
                        devLeaseNumber,
                        "Dev Renter (" + email + ")"
                )
        ));
    }

    private record DevSetupResponse(
            String signInUrl,
            UUID tenantProfileId,
            String clerkUserId,
            String landlordTenantId,
            UUID propertyId,
            UUID unitId,
            String leaseNumber,
            String name
    ) {}
}