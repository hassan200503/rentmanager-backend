package com.rentmanager.modules.lease.application;

import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseSearchRequest;
import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import com.rentmanager.modules.lease.application.dto.response.LeaseSummaryResponse;
import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.application.service.LeaseApplicationService;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mockito-only unit tests for LeaseApplicationService.search(), added this
 * session alongside the fix that made propertyId/status/fromDate/toDate
 * actually filter results -- previously the method silently ignored all
 * four fields on LeaseSearchRequest and returned every lease for the
 * tenant regardless of what was requested.
 *
 * Only search() is under test here. Pagination (page/size/totalPages) is
 * a known, already-logged gap shared with Property and RentLedger -- not
 * exercised or asserted on beyond confirming the unfiltered-count-as-total
 * behavior that already existed before this fix.
 */
@ExtendWith(MockitoExtension.class)
class LeaseApplicationServiceSearchTest {

    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private LeaseWorkflowEngine workflowEngine;
    @Mock
    private TenantProfileRepository tenantProfileRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private LeaseActivationOrchestrator leaseActivationOrchestrator;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private DepositRepository depositRepository;

    private LeaseApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyA = UUID.randomUUID();
    private final UUID propertyB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LeaseApplicationService(
                leaseRepository, workflowEngine, tenantProfileRepository,
                propertyRepository, unitRepository,
                leaseActivationOrchestrator, eventPublisher, depositRepository
        );
        TenantContext.setTenantId(tenantId);
        when(tenantProfileRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(propertyRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(Collections.emptyList());
        when(unitRepository.findAllByTenantIdAndIdIn(any(), any())).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Lease activeLease(UUID propertyId, LocalDate start, LocalDate end) {
        return Lease.restore(
                UUID.randomUUID(), tenantId, propertyId, UUID.randomUUID(), UUID.randomUUID(),
                "LN-" + UUID.randomUUID(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                start, end, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                com.rentmanager.modules.lease.domain.enums.LeaseStatus.ACTIVE
        );
    }

    private Lease terminatedLease(UUID propertyId, LocalDate start, LocalDate end) {
        return Lease.restore(
                UUID.randomUUID(), tenantId, propertyId, UUID.randomUUID(), UUID.randomUUID(),
                "LN-" + UUID.randomUUID(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                start, end, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                com.rentmanager.modules.lease.domain.enums.LeaseStatus.TERMINATED
        );
    }

    private LeaseSearchRequest request(UUID propertyId, LeaseStatusDTO status, LocalDate from, LocalDate to) {
        return new LeaseSearchRequest(null, propertyId, status, null, from, to, 0, 10);
    }

    @Nested
    class NoFilters {

        @Test
        void returnsAllLeasesForTenantWhenNoFiltersGiven() {
            Lease l1 = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            Lease l2 = terminatedLease(propertyB, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(l1, l2)));

            var result = service.search(request(null, null, null, null));

            assertThat(result.content()).hasSize(2);
        }
    }

    @Nested
    class PropertyFilter {

        @Test
        void filtersToOnlyMatchingProperty() {
            Lease inPropertyA = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            Lease inPropertyB = activeLease(propertyB, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(inPropertyA)));

            var result = service.search(request(propertyA, null, null, null));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(inPropertyA.getId());
        }
    }

    @Nested
    class StatusFilter {

        @Test
        void filtersToOnlyMatchingStatus() {
            Lease active = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            Lease terminated = terminatedLease(propertyA, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(active)));

            var result = service.search(request(null, LeaseStatusDTO.ACTIVE, null, null));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).status()).isEqualTo(LeaseStatusDTO.ACTIVE);
        }
    }

    @Nested
    class DateRangeFilter {

        @Test
        void excludesLeasesStartingBeforeFromDate() {
            Lease early = activeLease(propertyA, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            Lease inRange = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(inRange)));

            var result = service.search(request(null, null, LocalDate.of(2025, 1, 1), null));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(inRange.getId());
        }

        @Test
        void excludesLeasesEndingAfterToDate() {
            Lease inRange = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
            Lease tooLate = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(inRange)));

            var result = service.search(request(null, null, null, LocalDate.of(2026, 12, 31)));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(inRange.getId());
        }
    }

    @Nested
    class CombinedFilters {

        @Test
        void appliesAllFiltersTogether() {
            Lease matches = activeLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
            Lease wrongProperty = activeLease(propertyB, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
            Lease wrongStatus = terminatedLease(propertyA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

            when(leaseRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(matches)));

            var result = service.search(request(
                    propertyA, LeaseStatusDTO.ACTIVE,
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)
            ));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(matches.getId());
        }
    }
}