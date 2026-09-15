package com.rentmanager.modules.deposit.infrastructure.persistence.adapter;

import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.deposit.infrastructure.persistence.entity.DepositJpaEntity;
import com.rentmanager.modules.deposit.infrastructure.persistence.repository.DepositJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DepositRepositoryAdapter implements DepositRepository {

    private final DepositJpaRepository jpaRepository;

    @Override
    @Transactional
    public Deposit save(Deposit deposit) {

        if (deposit == null) {
            throw new IllegalArgumentException("Deposit cannot be null");
        }

        DepositJpaEntity entity;

        if (deposit.getId() != null) {
            entity = jpaRepository.findById(deposit.getId())
                    .map(existing -> {
                        updateEntity(deposit, existing);
                        return existing;
                    })
                    .orElseGet(() -> toEntity(deposit));
        } else {
            entity = toEntity(deposit);
        }

        DepositJpaEntity saved = jpaRepository.save(entity);

        return toDomain(saved);
    }

    @Override
    public Optional<Deposit> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Deposit> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Deposit> findByLeaseId(UUID leaseId) {
        return jpaRepository.findByLeaseId(leaseId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Deposit> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId) {
        return jpaRepository.findByLeaseIdAndTenantId(leaseId, tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<Deposit> findByTenantProfileId(UUID tenantProfileId) {
        return jpaRepository.findByTenantProfileId(tenantProfileId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Deposit> findByPendingRefundCheckoutRequestId(String checkoutRequestId) {
        return jpaRepository.findByPendingRefundCheckoutRequestId(checkoutRequestId)
                .map(this::toDomain);
    }

    @Override
    public List<Deposit> findAllByTenantIdAndStatus(UUID tenantId, DepositStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByPaidAtDesc(tenantId, status)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Deposit> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByPaidAtDesc(tenantId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(Deposit deposit) {
        jpaRepository.deleteById(deposit.getId());
    }

    // =========================================================
    // MAPPING
    // =========================================================

    private Deposit toDomain(DepositJpaEntity e) {

        if (e == null) return null;

        return Deposit.rehydrate(
                e.getId(),
                e.getTenantId(),
                e.getLeaseId(),
                e.getUnitId(),
                e.getTenantProfileId(),
                e.getAmountRequired(),
                e.getAmountPaid(),
                e.getAmountRefunded(),
                e.getStatus(),
                e.getPaidAt(),
                e.getRefundedAt(),
                e.getCurrency(),
                e.getDeductionAmount(),
                e.getDeductionReason(),
                e.getRefundReference(),
                e.getRefundRemarks(),
                e.getPendingRefundCheckoutRequestId(),
                e.getPendingRefundPhone(),
                e.getPendingRefundDeduction(),
                e.getPendingRefundDeductionReason(),
                e.getPendingRefundRemarks(),
                e.getPendingRefundInitiatedAt()
        );
    }

    private DepositJpaEntity toEntity(Deposit d) {

        if (d == null) return null;

        DepositJpaEntity e = new DepositJpaEntity();

        if (d.getId() != null) {
            e.setId(d.getId());
        }

        e.assignTenant(d.getTenantId());

        e.setLeaseId(d.getLeaseId());
        e.setUnitId(d.getUnitId());
        e.setTenantProfileId(d.getTenantProfileId());
        e.setAmountRequired(d.getAmountRequired());
        e.setAmountPaid(d.getAmountPaid());
        e.setAmountRefunded(d.getAmountRefunded());
        e.setStatus(d.getStatus());
        e.setPaidAt(d.getPaidAt());
        e.setRefundedAt(d.getRefundedAt());
        e.setCurrency(d.getCurrency());
        e.setDeductionAmount(d.getDeductionAmount());
        e.setDeductionReason(d.getDeductionReason());
        e.setRefundReference(d.getRefundReference());
        e.setRefundRemarks(d.getRefundRemarks());
        e.setPendingRefundCheckoutRequestId(d.getPendingRefundCheckoutRequestId());
        e.setPendingRefundPhone(d.getPendingRefundPhone());
        e.setPendingRefundDeduction(d.getPendingRefundDeduction());
        e.setPendingRefundDeductionReason(d.getPendingRefundDeductionReason());
        e.setPendingRefundRemarks(d.getPendingRefundRemarks());
        e.setPendingRefundInitiatedAt(d.getPendingRefundInitiatedAt());

        return e;
    }

    private void updateEntity(Deposit d, DepositJpaEntity e) {
        e.setAmountPaid(d.getAmountPaid());
        e.setAmountRefunded(d.getAmountRefunded());
        e.setStatus(d.getStatus());
        e.setPaidAt(d.getPaidAt());
        e.setRefundedAt(d.getRefundedAt());
        e.setDeductionAmount(d.getDeductionAmount());
        e.setDeductionReason(d.getDeductionReason());
        e.setRefundReference(d.getRefundReference());
        e.setRefundRemarks(d.getRefundRemarks());
        e.setPendingRefundCheckoutRequestId(d.getPendingRefundCheckoutRequestId());
        e.setPendingRefundPhone(d.getPendingRefundPhone());
        e.setPendingRefundDeduction(d.getPendingRefundDeduction());
        e.setPendingRefundDeductionReason(d.getPendingRefundDeductionReason());
        e.setPendingRefundRemarks(d.getPendingRefundRemarks());
        e.setPendingRefundInitiatedAt(d.getPendingRefundInitiatedAt());
    }
}