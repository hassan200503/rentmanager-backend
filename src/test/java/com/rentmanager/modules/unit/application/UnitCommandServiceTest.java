package com.rentmanager.modules.unit.application;

import com.rentmanager.modules.unit.application.command.service.UnitCommandServiceImpl;
import com.rentmanager.modules.unit.application.command.validator.*;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitCommandServiceTest {

    @InjectMocks
    private UnitCommandServiceImpl service;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private CreateUnitValidator createUnitValidator;

    @Mock
    private UpdateUnitValidator updateUnitValidator;

    @Mock
    private ActivateUnitValidator activateUnitValidator;

    @Mock
    private ArchiveUnitValidator archiveUnitValidator;

    @Mock
    private MarkOccupiedValidator markOccupiedValidator;

    @Mock
    private UnitMarkVacantValidator unitMarkVacantValidator;

    // =====================================================
    // CREATE
    // =====================================================

    @Test
    void should_create_unit_and_persist() {

        UUID tenantId = UUID.randomUUID();
        CreateUnitRequest request = mock(CreateUnitRequest.class);

        when(request.getPropertyId()).thenReturn(UUID.randomUUID());
        when(request.getUnitNumber()).thenReturn("U-1");
        when(request.getLabel()).thenReturn("Label");
        when(request.getRentAmount()).thenReturn(new BigDecimal("1000"));
        when(request.getDescription()).thenReturn("desc");

        Unit unit = mock(Unit.class);
        UnitResponse response = mock(UnitResponse.class);

        when(unitRepository.save(any(Unit.class))).thenReturn(unit);
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());
        when(unitMapper.toResponse(unit)).thenReturn(response);

        UnitResponse result = service.create(tenantId, request);

        verify(createUnitValidator).validate(tenantId, request);
        verify(unitRepository).save(any(Unit.class));
        verify(eventPublisher).publishAll(anyList());
        verify(unitMapper).toResponse(unit);

        assertNotNull(result);
    }

    // =====================================================
    // ACTIVATE
    // =====================================================

    @Test
    void should_activate_unit() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(activateUnitValidator.validate(tenantId, unitId)).thenReturn(unit);
        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());

        service.activate(tenantId, unitId, "corr");

        verify(unit).activate(anyString());
        verify(unitRepository).save(unit);
        verify(eventPublisher).publishAll(anyList());
    }

    // =====================================================
    // ARCHIVE
    // =====================================================

    @Test
    void should_archive_unit() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(archiveUnitValidator.validate(tenantId, unitId)).thenReturn(unit);
        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());

        service.archive(tenantId, unitId);

        verify(unit).archive(anyString());
        verify(unitRepository).save(unit);
        verify(eventPublisher).publishAll(anyList());
    }

    // =====================================================
    // OCCUPANCY
    // =====================================================

    @Test
    void should_mark_unit_occupied() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(markOccupiedValidator.validate(tenantId, unitId)).thenReturn(unit);
        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());

        service.markOccupied(tenantId, unitId, "corr");

        verify(unit).markOccupied(anyString());
        verify(unitRepository).save(unit);
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    void should_mark_unit_vacant() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(unitMarkVacantValidator.validate(tenantId, unitId)).thenReturn(unit);
        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());

        service.markVacant(tenantId, unitId, "corr");

        verify(unit).markVacant(anyString());
        verify(unitRepository).save(unit);
        verify(eventPublisher).publishAll(anyList());
    }

    // =====================================================
    // CORRELATION FALLBACK
    // =====================================================

    @Test
    void should_use_system_correlation_when_blank() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(activateUnitValidator.validate(tenantId, unitId)).thenReturn(unit);
        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unit.pullDomainEvents()).thenReturn(Collections.emptyList());

        service.activate(tenantId, unitId, "");

        verify(unit).activate(eq("SYSTEM"));
        verify(eventPublisher).publishAll(anyList());
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Test
    void should_update_unit() {

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        UpdateUnitRequest request = new UpdateUnitRequest();
        request.setUnitNumber("U-2");
        request.setLabel("Updated");
        request.setRentAmount(new BigDecimal("2000"));
        request.setDescription("updated");

        Unit unit = mock(Unit.class);

        when(updateUnitValidator.validate(tenantId, unitId)).thenReturn(unit);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(tenantId, unitId, request)
        );

        assertEquals("unit_number cannot be modified", ex.getMessage());

        verify(updateUnitValidator).validate(tenantId, unitId);
        verifyNoInteractions(unitRepository);
        verifyNoInteractions(unitMapper);
        verifyNoInteractions(eventPublisher);
    }
}
