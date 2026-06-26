package com.rentmanager.modules.unit.application.command.service;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.application.command.validator.*;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ConflictException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@RequiredArgsConstructor
@Transactional
public class UnitCommandServiceImpl implements UnitCommandService {

    @PersistenceContext
    private EntityManager entityManager;
    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;
    private final DomainEventPublisher eventPublisher;

    private final CreateUnitValidator createUnitValidator;
    private final UpdateUnitValidator updateUnitValidator;
    private final ActivateUnitValidator activateUnitValidator;
    private final ArchiveUnitValidator archiveUnitValidator;
    private final MarkOccupiedValidator markOccupiedValidator;
    private final UnitMarkVacantValidator unitMarkVacantValidator;

    @Override
    public UnitResponse create(UUID tenantId, CreateUnitRequest request) {

        createUnitValidator.validate(tenantId, request);

        Unit unit = Unit.create(
                tenantId,
                request.getPropertyId(),
                request.getUnitNumber(),
                request.getLabel(),
                request.getRentAmount(),
                request.getDescription(),
                generateCorrelationId()
        );

        Unit saved;
        try {
            saved = unitRepository.save(unit);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "Unit number '" + request.getUnitNumber() + "' already exists in this property"
            );
        }

        eventPublisher.publishAll(saved.pullDomainEvents());

        return unitMapper.toResponse(saved);
    }

    @Override
    public UnitResponse update(UUID tenantId, UUID unitId, UpdateUnitRequest request) {

        Unit unit = updateUnitValidator.validate(tenantId, unitId);

        if (request.getUnitNumber() != null) {
            throw new IllegalArgumentException("unit_number cannot be modified");
        }

        unit.updateDetails(
                unit.getUnitNumber(),
                request.getLabel(),
                request.getRentAmount(),
                request.getDescription(),
                generateCorrelationId()
        );

        Unit saved = unitRepository.save(unit);

        eventPublisher.publishAll(saved.pullDomainEvents());

        return unitMapper.toResponse(saved);
    }

    @Override
    public void activate(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = activateUnitValidator.validate(tenantId, unitId);

        unit.activate(resolveCorrelationId(correlationId));

        Unit saved = unitRepository.save(unit);

        eventPublisher.publishAll(saved.pullDomainEvents());
    }

    @Override
    public void archive(UUID tenantId, UUID unitId) {

        Unit unit = archiveUnitValidator.validate(tenantId, unitId);

        unit.archive("SYSTEM");

        Unit saved = unitRepository.save(unit);

        eventPublisher.publishAll(saved.pullDomainEvents());
    }

    @Override
    public void markOccupied(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = markOccupiedValidator.validate(tenantId, unitId);

        unit.markOccupied(resolveCorrelationId(correlationId));

        Unit saved = unitRepository.save(unit);

        eventPublisher.publishAll(saved.pullDomainEvents());
    }

    @Override
    public void markVacant(UUID tenantId, UUID unitId, String correlationId) {

        Unit unit = unitMarkVacantValidator.validate(tenantId, unitId);

        unit.markVacant(resolveCorrelationId(correlationId));

        Unit saved = unitRepository.save(unit);

        eventPublisher.publishAll(saved.pullDomainEvents());
    }

    private String generateCorrelationId() {
        return "CORR-" + System.currentTimeMillis();
    }

    private String resolveCorrelationId(String correlationId) {
        return (correlationId == null || correlationId.isBlank())
                ? "SYSTEM"
                : correlationId;
    }
}