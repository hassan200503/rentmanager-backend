package com.rentmanager.modules.unit.application;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.application.dto.response.UnitReservationSummaryResponse;
import com.rentmanager.modules.unit.application.query.service.UnitReservationSummaryQueryServiceImpl;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitReservationSummaryQueryServiceImplTest {

    @InjectMocks
    private UnitReservationSummaryQueryServiceImpl service;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private final UUID UNIT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    // =====================================================
    // getSummary() — PUBLIC LISTING HARDENING (2026-07-08, Phase 2)
    // =====================================================

    @Test
    void shouldThrow404_whenUnitIsNotPubliclyVisible() {
        // Covers: unit doesn't exist, OR exists but UnitStatus != ACTIVE,
        // OR occupancyStatus != VACANT, OR parent Property.status != ACTIVE.
        // All are indistinguishable at this layer — the publicly-visible
        // repository call simply returns empty — matching 404-not-403.
        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSummary(UNIT_ID));

        // The old unscoped lookup must never be used by this public path.
        verify(unitRepository, never()).findById(UNIT_ID);
        verify(propertyRepository, never()).findById(any());
    }

    @Test
    void shouldThrow404_whenPropertyRowIsMissing_despiteUnitBeingPubliclyVisible() {
        // Defensive case, not a security check: findPubliclyVisibleVacantUnitById
        // already guarantees the parent property is ACTIVE via its join, so
        // this simulates a data-integrity gap (e.g. an orphaned propertyId)
        // rather than a status-filtering failure.
        Unit unit = mock(Unit.class);
        when(unit.getPropertyId()).thenReturn(PROPERTY_ID);

        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.of(unit));

        when(propertyRepository.findById(PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSummary(UNIT_ID));
    }

    @Test
    void shouldReturnSummary_whenUnitIsPubliclyVisible() {

        Unit unit = mock(Unit.class);
        when(unit.getId()).thenReturn(UNIT_ID);
        when(unit.getPropertyId()).thenReturn(PROPERTY_ID);
        when(unit.getUnitNumber()).thenReturn("A101");
        when(unit.getRentAmount()).thenReturn(BigDecimal.valueOf(25000));

        Property property = mock(Property.class);
        when(property.getName()).thenReturn("Kilimani Heights");

        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.of(unit));

        when(propertyRepository.findById(PROPERTY_ID))
                .thenReturn(Optional.of(property));

        UnitReservationSummaryResponse result = service.getSummary(UNIT_ID);

        assertNotNull(result);
        assertEquals(UNIT_ID, result.getUnitId());
        assertEquals("A101", result.getUnitNumber());
        assertEquals("Kilimani Heights", result.getPropertyName());
        assertEquals(BigDecimal.valueOf(25000), result.getMonthlyRent());
        // DEPOSIT_MONTHS = 2, computed as rentAmount * 2 — see TODO in impl
        // flagging this constant as an unconfirmed business-logic concern.
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(result.getDepositAmount()));

        verify(unitRepository, never()).findById(UNIT_ID);
    }
}