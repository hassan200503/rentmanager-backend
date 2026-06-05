package com.rentmanager.modules.unit.application.command.service;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.application.command.validator.*;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UnitCommandServiceImpl implements UnitCommandService {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    private final CreateUnitValidator createUnitValidator;
    private final UpdateUnitValidator updateUnitValidator;
    private final ActivateUnitValidator activateUnitValidator;
    private final ArchiveUnitValidator archiveUnitValidator;
    private final MarkOccupiedValidator markOccupiedValidator;
    private final UnitMarkVacantValidator unitMarkVacantValidator;

    // =====================================================
    // CREATE
    // =====================================================
    @Override
    public UnitResponse create(UUID tenantId, CreateUnitRequest request) {

        createUnitValidator.validate(tenantId, request);

        Unit unit = Unit.create(
                tenantId,
                request.getPropertyId(),
                request.getUnitNumber(),
                request.getLabel(),
                request.getRentAmount(), // BigDecimal (NO Double)
                request.getDescription(),
                generateCorrelationId()
        );

        return unitMapper.toResponse(unitRepository.save(unit));
    }

    // =====================================================
    // UPDATE
    // =====================================================
    @Override
    public UnitResponse update(UUID tenantId, UUID unitId, UpdateUnitRequest request) {

        Unit unit = updateUnitValidator.validate(tenantId, unitId);

        unit.updateDetails(
                request.getUnitNumber(),
                request.getLabel(),
                request.getRentAmount(), // BigDecimal (NO Double)
                request.getDescription(),
                generateCorrelationId()
        );

        return unitMapper.toResponse(unitRepository.save(unit));
    }

    // =====================================================
    // ACTIVATE
    // =====================================================
    @Override
    public void activate(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = activateUnitValidator.validate(tenantId, unitId);

        unit.activate(resolveCorrelationId(correlationId));

        unitRepository.save(unit);
    }

    // =====================================================
    // ARCHIVE
    // =====================================================
    @Override
    public void archive(UUID tenantId, UUID unitId) {

        Unit unit = archiveUnitValidator.validate(tenantId, unitId);

        unit.archive("SYSTEM");

        unitRepository.save(unit);
    }

    // =====================================================
    // MARK OCCUPIED
    // =====================================================
    @Override
    public void markOccupied(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = markOccupiedValidator.validate(tenantId, unitId);

        unit.markOccupied(resolveCorrelationId(correlationId));

        unitRepository.save(unit);
    }

    // =====================================================
    // MARK VACANT
    // =====================================================
    @Override
    public void markVacant(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = unitMarkVacantValidator.validate(tenantId, unitId);

        unit.markVacant(resolveCorrelationId(correlationId));

        unitRepository.save(unit);
    }

    // =====================================================
    // CORRELATION HELPERS
    // =====================================================
    private String generateCorrelationId() {
        return "CORR-" + System.currentTimeMillis();
    }

    private String resolveCorrelationId(String correlationId) {
        return (correlationId == null || correlationId.isBlank())
                ? "SYSTEM"
                : correlationId;
    }
}