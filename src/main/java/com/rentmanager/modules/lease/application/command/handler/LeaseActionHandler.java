package com.rentmanager.modules.lease.application.command.handler;

import com.rentmanager.contract.lease.request.LeaseActionRequest;
import com.rentmanager.contract.lease.request.LeaseActionType;
import com.rentmanager.modules.lease.application.command.validator.LeaseActionValidator;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.lease.infrastructure.persistence.mapper.LeaseMapper;
import com.rentmanager.modules.lease.infrastructure.persistence.repository.JpaLeaseRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.rentmanager.shared.security.context.TenantContext;

import java.util.UUID;


@Component
public class LeaseActionHandler {

    private final LeaseRepository leaseRepository;
    private final LeaseActionValidator validator;
    private final JpaLeaseRepository jpaLeaseRepository;
    private final LeaseMapper leaseMapper;

    public LeaseActionHandler(
            LeaseRepository leaseRepository,
            LeaseActionValidator validator,
            JpaLeaseRepository jpaLeaseRepository,
            LeaseMapper leaseMapper
    ) {
        this.leaseRepository = leaseRepository;
        this.validator = validator;
        this.jpaLeaseRepository = jpaLeaseRepository;
        this.leaseMapper = leaseMapper;
    }

    // ✅ FIXED: leaseId added
    public void handle(UUID leaseId, LeaseActionRequest request) {

        // 1. Validate request
        validator.validate(request);

        // 2. Tenant context
        UUID tenantId = TenantContext.getTenantId();

        // 3. Load aggregate (FIXED)
        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lease not found: " + leaseId,
                        ErrorCode.LEASE_NOT_FOUND
                ));

        // 4. Execute domain action
        switch (request.getAction()) {

            case ACTIVATE -> lease.activate();

            case APPROVE -> lease.approve();

            case REJECT -> lease.reject(request.getReason());

            case TERMINATE -> lease.terminate(
                    request.getTerminationType(),
                    request.getReason(),
                    request.getActor(),
                    lease.getTenantId()
            );

            case RENEW -> lease.renew(
                    request.getActionDate(),
                    request.getActionDate() != null
                            ? request.getActionDate().plusMonths(12)
                            : null,
                    lease.getTenantId(),
                    request.getActor()
            );
        }

        // 5. Persist changes
        save(lease);
    }

    public Lease save(Lease lease) {

        LeaseEntity entity = jpaLeaseRepository
                .findById(lease.getId())
                .map(existing -> {
                    leaseMapper.updateEntity(existing, lease);
                    return existing;
                })
                .orElseGet(() -> leaseMapper.toEntity(lease));

        LeaseEntity saved = jpaLeaseRepository.save(entity);

        return leaseMapper.toDomain(saved);
    }
}