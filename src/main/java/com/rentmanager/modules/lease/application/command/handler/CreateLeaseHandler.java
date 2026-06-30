package com.rentmanager.modules.lease.application.command.handler;

import com.rentmanager.modules.lease.application.command.CreateLeaseCommand;
import com.rentmanager.modules.lease.application.command.service.LeaseNumberGenerator;
import com.rentmanager.modules.lease.application.command.usecase.CreateLeaseUseCase;
import com.rentmanager.modules.lease.application.command.validator.CreateLeaseValidator;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates manual lease creation.
 *
 * Execution order:
 * 1. Validate application rules
 * 2. Generate lease number
 * 3. Create aggregate
 * 4. Advance workflow to awaiting deposit
 * 5. Persist
 * 6. Publish domain events
 */
@Service
@RequiredArgsConstructor
public class CreateLeaseHandler implements CreateLeaseUseCase {

    private final CreateLeaseValidator validator;
    private final LeaseRepository leaseRepository;
    private final LeaseNumberGenerator leaseNumberGenerator;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public UUID handle(CreateLeaseCommand cmd) {

        // 1. Application validation
        validator.validate(cmd);

        // 2. Generate lease number
        String leaseNumber = leaseNumberGenerator.generate(
                cmd.propertyId(),
                cmd.unitId()
        );

        // 3. Create aggregate
        Lease lease = Lease.create(
                cmd.tenantId(),
                cmd.propertyId(),
                cmd.unitId(),
                cmd.tenantProfileId(),
                leaseNumber,
                cmd.leaseType(),
                cmd.billingCycle(),
                cmd.startDate(),
                cmd.endDate(),
                cmd.monthlyRent(),
                cmd.securityDeposit(),
                cmd.lateFeeAmount(),
                cmd.gracePeriodDays(),
                cmd.autoRenew()
        );

        // 4. Advance manual lease workflow
        lease.approve();
        lease.markAwaitingDeposit();

        // 5. Persist
        lease = leaseRepository.save(lease);

        // 6. Publish domain events
        eventPublisher.publishAll(lease.pullDomainEvents());

        return lease.getId();
    }
}