package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.tax.application.config.TaxProperties;
import com.rentmanager.modules.tax.application.port.ResidentialRentPaymentAggregationPort;
import com.rentmanager.modules.tax.domain.event.MonthlyFilingComputed;
import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;
import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.domain.repository.MonthlyRentalIncomeFilingRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonthlyRentalFilingComputationServiceTest {

    private MonthlyRentalIncomeFilingRepository filingRepository;
    private ResidentialRentPaymentAggregationPort residentialPayments;
    private MriRatePolicyService mriRatePolicy;
    private DomainEventPublisher eventPublisher;
    private TaxProperties taxProperties;
    private MonthlyRentalFilingComputationService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filingRepository = mock(MonthlyRentalIncomeFilingRepository.class);
        residentialPayments = mock(ResidentialRentPaymentAggregationPort.class);
        mriRatePolicy = mock(MriRatePolicyService.class);
        eventPublisher = mock(DomainEventPublisher.class);
        taxProperties = new TaxProperties();
        service = new MonthlyRentalFilingComputationService(
                filingRepository, residentialPayments, mriRatePolicy,
                eventPublisher, taxProperties);
    }

    @Test
    void computeForPeriod_normalisesMonthAndAppliesActiveRate() {
        MriRateSchedule active = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), null,
                new BigDecimal("0.0750"), "Finance Act 2023");
        active.activate();

        when(filingRepository.findByTenantIdAndPeriod(eq(tenantId), eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(Optional.empty());
        when(mriRatePolicy.activeRateAsOf(LocalDate.of(2026, 7, 1))).thenReturn(active);
        when(residentialPayments.sumResidentialPaymentsInPeriod(
                eq(tenantId), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 8, 1))))
                .thenReturn(new BigDecimal("200000.00"));
        when(filingRepository.save(any(MonthlyRentalIncomeFiling.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<MonthlyRentalIncomeFiling> result =
                service.computeForPeriod(tenantId, LocalDate.of(2026, 7, 15));

        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2026, 7, 1), result.get().getPeriod());
        assertEquals(new BigDecimal("200000.00"), result.get().getGrossRentalIncome());
        assertEquals(new BigDecimal("15000.00"), result.get().getMriTaxDue());
        assertFalse(result.get().isNilReturn());
        verify(eventPublisher).publish(any(MonthlyFilingComputed.class));
    }

    @Test
    void computeForPeriod_zeroPaymentsProducesNilReturn() {
        MriRateSchedule active = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), null,
                new BigDecimal("0.0750"), "Finance Act 2023");
        active.activate();

        when(filingRepository.findByTenantIdAndPeriod(eq(tenantId), eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(Optional.empty());
        when(mriRatePolicy.activeRateAsOf(LocalDate.of(2026, 7, 1))).thenReturn(active);
        when(residentialPayments.sumResidentialPaymentsInPeriod(
                eq(tenantId), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 8, 1))))
                .thenReturn(BigDecimal.ZERO);
        when(filingRepository.save(any(MonthlyRentalIncomeFiling.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<MonthlyRentalIncomeFiling> result =
                service.computeForPeriod(tenantId, LocalDate.of(2026, 7, 1));

        assertTrue(result.isPresent());
        assertTrue(result.get().isNilReturn());
        assertEquals(BigDecimal.ZERO.setScale(2), result.get().getGrossRentalIncome());
        assertEquals(BigDecimal.ZERO.setScale(2), result.get().getMriTaxDue());
    }

    @Test
    void computeForPeriod_isIdempotentWhenFilingAlreadyExists() {
        MonthlyRentalIncomeFiling existing = MonthlyRentalIncomeFiling.compute(
                tenantId, LocalDate.of(2026, 7, 1),
                new BigDecimal("50000.00"), new BigDecimal("0.0750"));

        when(filingRepository.findByTenantIdAndPeriod(eq(tenantId), eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(Optional.of(existing));

        Optional<MonthlyRentalIncomeFiling> result =
                service.computeForPeriod(tenantId, LocalDate.of(2026, 7, 1));

        assertTrue(result.isPresent());
        assertSame(existing, result.get());
        verify(filingRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void computeForPeriod_throwsWhenNoActiveRateCoversTheMonth() {
        when(filingRepository.findByTenantIdAndPeriod(eq(tenantId), eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(Optional.empty());
        when(mriRatePolicy.activeRateAsOf(LocalDate.of(2026, 7, 1)))
                .thenThrow(new IllegalStateException("No active MRI rate"));

        assertThrows(IllegalStateException.class,
                () -> service.computeForPeriod(tenantId, LocalDate.of(2026, 7, 1)));
        verify(filingRepository, never()).save(any());
    }

    @Test
    void computeForPeriod_skipsWhenDisabled() {
        taxProperties.setFilingEnabled(false);

        Optional<MonthlyRentalIncomeFiling> result =
                service.computeForPeriod(tenantId, LocalDate.of(2026, 7, 1));

        assertTrue(result.isEmpty());
        verify(filingRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void computeForPeriod_rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> service.computeForPeriod(null, LocalDate.of(2026, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.computeForPeriod(tenantId, null));
    }
}