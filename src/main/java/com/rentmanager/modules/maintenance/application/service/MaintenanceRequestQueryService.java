package com.rentmanager.modules.maintenance.application.service;

import com.rentmanager.modules.maintenance.application.dto.MaintenanceSlaSummaryResponse;
import com.rentmanager.modules.maintenance.api.dto.MaintenanceRequestResponse;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceRequestQueryService {

    /**
     * Phase 4a/5: an "excellent" response-time rating is only ever claimed
     * once the tenant has at least this many resolved requests. A badge on
     * one or two jobs is dishonest.
     */
    public static final int MIN_RESOLVED_REQUESTS_FOR_SLA = 5;

    private static final int SLA_EXCELLENT_HOURS = 24;

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final TenantProfileRepository tenantProfileRepository;

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findAllByTenantId(UUID tenantId) {
        return maintenanceRequestRepository.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public MaintenanceRequest findByIdAndTenantId(UUID id, UUID tenantId) {
        return maintenanceRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return maintenanceRequestRepository.findByTenantIdAndUnitId(tenantId, unitId);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findByTenantIdAndTenantProfileId(UUID tenantId, UUID tenantProfileId) {
        return maintenanceRequestRepository.findByTenantIdAndTenantProfileId(tenantId, tenantProfileId);
    }

    /**
     * Phase 4a/5: enriched Requests-hub list with optional status/priority
     * filters and createdAt/priority/status/title/updatedAt sorting.
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRequestResponse> getRequests(
            UUID tenantId,
            MaintenanceRequestStatus status,
            MaintenancePriority priority,
            String sort,
            String direction
    ) {
        List<MaintenanceRequest> requests = find(tenantId, status, priority);
        Enrichment enrichment = new Enrichment(tenantId, requests);
        return requests.stream()
                .sorted(comparator(sort, direction))
                .map(enrichment::enrich)
                .toList();
    }

    /**
     * V54: how many requests the landlord has not seen yet. Powers the
     * sidebar badge; the hub marks them viewed via markAllViewed.
     */
    @Transactional(readOnly = true)
    public long countUnviewed(UUID tenantId) {
        return maintenanceRequestRepository.countUnviewedByTenantId(tenantId);
    }

    /**
     * Phase 4a/5: SLA summary for the landlord Requests hub.
     */
    @Transactional(readOnly = true)
    public MaintenanceSlaSummaryResponse getSlaSummary(UUID tenantId) {
        List<MaintenanceRequest> requests = maintenanceRequestRepository.findAllByTenantId(tenantId);

        long resolved = requests.stream()
                .filter(request -> request.getStatus() == MaintenanceRequestStatus.COMPLETED)
                .count();

        long responded = requests.stream()
                .filter(request -> request.getFirstLandlordResponseAt() != null)
                .count();

        long respondedWithin24h = requests.stream()
                .filter(request -> request.getFirstLandlordResponseAt() != null)
                .filter(request -> Duration.between(
                                request.getCreatedAt(), toInstant(request.getFirstLandlordResponseAt()))
                        .toHours() <= SLA_EXCELLENT_HOURS)
                .count();

        double avgResponseHours = responded == 0 ? 0.0 : Math.round(
                requests.stream()
                        .filter(request -> request.getFirstLandlordResponseAt() != null)
                        .mapToDouble(request -> Duration.between(
                                        request.getCreatedAt(), toInstant(request.getFirstLandlordResponseAt()))
                                .toMinutes() / 60.0)
                        .average()
                        .orElse(0.0) * 10.0) / 10.0;

        boolean resolvedRequirementMet = resolved >= MIN_RESOLVED_REQUESTS_FOR_SLA;
        Integer responseRatePct = (resolvedRequirementMet && responded > 0)
                ? Math.round(((float) respondedWithin24h / responded) * 100)
                : null;

        return new MaintenanceSlaSummaryResponse(
                requests.size(),
                (int) resolved,
                (int) responded,
                avgResponseHours,
                resolvedRequirementMet,
                responseRatePct
        );
    }

    private List<MaintenanceRequest> find(
            UUID tenantId, MaintenanceRequestStatus status, MaintenancePriority priority) {
        if (status != null && priority != null) {
            return maintenanceRequestRepository.findByTenantIdAndPriorityAndStatus(tenantId, priority, status);
        }
        if (status != null) {
            return maintenanceRequestRepository.findByTenantIdAndStatus(tenantId, status);
        }
        if (priority != null) {
            return maintenanceRequestRepository.findByTenantIdAndPriority(tenantId, priority);
        }
        return maintenanceRequestRepository.findAllByTenantId(tenantId);
    }

    private Comparator<MaintenanceRequest> comparator(String sort, String direction) {
        Comparator<MaintenanceRequest> comparator = switch (sort == null ? "createdAt" : sort) {
            case "priority" -> Comparator.comparingInt(
                    request -> priorityWeight(request.getPriority()));
            case "status" -> Comparator.comparing(request -> request.getStatus().name());
            case "title" -> Comparator.comparing(MaintenanceRequest::getTitle,
                    Comparator.nullsLast(String::compareTo));
            case "updatedAt" -> Comparator.comparing(MaintenanceRequest::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(MaintenanceRequest::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
    }

    private static int priorityWeight(MaintenancePriority priority) {
        return switch (priority) {
            case URGENT -> 3;
            case HIGH -> 2;
            case MEDIUM -> 1;
            case LOW -> 0;
        };
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Batch-lookups property/unit/renter names once per request set.
     * PropertyRepository and UnitRepository expose no batch finder, so we
     * build the maps with one call per distinct id (lists here are small).
     */
    private final class Enrichment {
        private final Map<UUID, String> propertyNames = new HashMap<>();
        private final Map<UUID, String> unitNumbers = new HashMap<>();
        private final Map<UUID, String> renterNames = new HashMap<>();
        private final UUID tenantId;

        private Enrichment(UUID tenantId, List<MaintenanceRequest> requests) {
            this.tenantId = tenantId;
            requests.forEach(request -> {
                if (request.getPropertyId() != null) {
                    propertyNames.computeIfAbsent(request.getPropertyId(), this::propertyName);
                }
                if (request.getUnitId() != null) {
                    unitNumbers.computeIfAbsent(request.getUnitId(), this::unitNumber);
                }
                if (request.getTenantProfileId() != null) {
                    renterNames.computeIfAbsent(request.getTenantProfileId(), this::renterName);
                }
            });
        }

        private String propertyName(UUID id) {
            return propertyRepository.findByIdAndTenantId(id, tenantId)
                    .map(Property::getName)
                    .orElse(null);
        }

        private String unitNumber(UUID id) {
            return unitRepository.findById(id)
                    .map(Unit::getUnitNumber)
                    .orElse(null);
        }

        private String renterName(UUID id) {
            return tenantProfileRepository.findById(id)
                    .map(TenantProfile::getFullName)
                    .orElse(null);
        }

        private MaintenanceRequestResponse enrich(MaintenanceRequest request) {
            return MaintenanceRequestResponse.from(
                    request,
                    propertyNames.get(request.getPropertyId()),
                    unitNumbers.get(request.getUnitId()),
                    renterNames.get(request.getTenantProfileId())
            );
        }
    }
}
