package com.rentmanager.modules.unit.application;

import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.application.query.service.PublicUnitQueryServiceImpl;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicUnitQueryServiceImplTest {

    @InjectMocks
    private PublicUnitQueryServiceImpl service;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UnitMediaRepository unitMediaRepository;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private PropertyRepository propertyRepository;

    private final UUID UNIT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    // =====================================================
    // getVacantUnits() — list endpoint
    // =====================================================

    @Test
    void shouldOnlyQueryPubliclyVisibleUnits_forListing() {

        Pageable pageable = mock(Pageable.class);
        Page<Unit> emptyPage = new PageImpl<>(List.of());

        when(unitRepository.findPubliclyVisibleVacantUnits(null, pageable))
                .thenReturn(emptyPage);

        when(unitMediaRepository.findAllByUnitIdIn(anyList()))
                .thenReturn(List.of());

        service.getVacantUnits(null, pageable);

        // The old occupancy-only methods must never be called by the
        // public path anymore.
        verify(unitRepository, never()).findByOccupancyStatus(any(), any());
        verify(unitRepository, never()).searchPublic(anyString(), any(), any());
        verify(unitRepository).findPubliclyVisibleVacantUnits(null, pageable);
    }

    @Test
    void shouldExcludeUnitWithInactiveStatus_evenIfOccupancyIsVacant() {
        // Simulates a unit whose own UnitStatus is INACTIVE/MAINTENANCE/
        // ARCHIVED but occupancyStatus happens to be VACANT: the
        // publicly-visible-scoped call just never returns it.
        Pageable pageable = mock(Pageable.class);
        Page<Unit> emptyPage = new PageImpl<>(List.of());

        when(unitRepository.findPubliclyVisibleVacantUnits(null, pageable))
                .thenReturn(emptyPage);

        Page<PublicUnitResponse> result = service.getVacantUnits(null, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldOnlyQueryPubliclyVisibleUnits_byProperty() {

        Pageable pageable = mock(Pageable.class);
        Page<Unit> emptyPage = new PageImpl<>(List.of());

        when(unitRepository.findPubliclyVisibleVacantUnitsByProperty(PROPERTY_ID, pageable))
                .thenReturn(emptyPage);

        when(unitMediaRepository.findAllByUnitIdIn(anyList()))
                .thenReturn(List.of());

        service.getVacantUnitsByProperty(PROPERTY_ID, pageable);

        verify(unitRepository, never())
                .findByPropertyIdAndOccupancyStatus(any(), any(), any());
        verify(unitRepository)
                .findPubliclyVisibleVacantUnitsByProperty(PROPERTY_ID, pageable);
    }

    @Test
    void shouldExcludeUnit_whenParentPropertyIsNotActive_evenIfUnitItselfIsActiveAndVacant() {
        // This is the join condition specifically: a unit correctly marked
        // ACTIVE + VACANT, sitting under a DRAFT/ARCHIVED property, must
        // still be excluded. At the service layer this is indistinguishable
        // from any other "not publicly visible" case — the repository call
        // simply returns empty — so this test mainly documents intent; real
        // verification of the join itself belongs in a repository-level
        // test (see note below).
        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getVacantUnitById(UNIT_ID));
    }

    // =====================================================
    // getVacantUnitById() — detail endpoint
    // =====================================================




    @Test
    void shouldReturnUnit_whenPubliclyVisible() {

        Unit unit = mock(Unit.class);
        // NOTE: no unit.getId() stub here — getVacantUnitById() uses the
        // unitId *method parameter* for the media lookup, never unit.getId().
        // Stubbing it anyway triggered Mockito's strict UnnecessaryStubbingException.

        PublicUnitResponse mapped = new PublicUnitResponse();
        mapped.setId(UNIT_ID);

        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.of(unit));

        when(unitMapper.toPublicResponse(unit)).thenReturn(mapped);

        when(unitMediaRepository.findAllByUnitId(UNIT_ID))
                .thenReturn(List.of());

        PublicUnitResponse result = service.getVacantUnitById(UNIT_ID);

        assertNotNull(result);
        assertEquals(UNIT_ID, result.getId());
        verify(unitRepository).findPubliclyVisibleVacantUnitById(UNIT_ID);
        verify(unitRepository, never()).findById(UNIT_ID);
    }







    @Test
    void shouldThrow404_whenUnitDoesNotExist() {

        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getVacantUnitById(UNIT_ID));
    }

    @Test
    void shouldThrow404_whenUnitExistsButIsNotActive() {
        // Same not-found path as "doesn't exist" — deliberately not
        // distinguishable, matching the 404-not-403 principle.
        when(unitRepository.findPubliclyVisibleVacantUnitById(UNIT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getVacantUnitById(UNIT_ID));

        verify(unitRepository, never()).findById(UNIT_ID);
    }

    // =====================================================
    // getLongestVacantUnit()
    // =====================================================

    @Test
    void shouldOnlyConsiderPubliclyVisibleUnits_forLongestVacant() {

        Unit unit = mock(Unit.class);
        when(unit.getId()).thenReturn(UNIT_ID);
        when(unit.getPropertyId()).thenReturn(PROPERTY_ID);

        PublicUnitResponse mapped = new PublicUnitResponse();
        mapped.setId(UNIT_ID);

        when(unitRepository.findPubliclyVisibleLongestVacantUnit())
                .thenReturn(Optional.of(unit));

        when(unitMapper.toPublicResponse(unit)).thenReturn(mapped);

        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

        when(unitMediaRepository.findAllByUnitId(UNIT_ID))
                .thenReturn(List.of());

        PublicUnitResponse result = service.getLongestVacantUnit();

        assertNotNull(result);
        verify(unitRepository).findPubliclyVisibleLongestVacantUnit();
        verify(unitRepository, never()).findLongestVacant();
    }

    @Test
    void shouldThrow404_whenNoPubliclyVisibleVacantUnitsExist() {

        when(unitRepository.findPubliclyVisibleLongestVacantUnit())
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getLongestVacantUnit());
    }
}