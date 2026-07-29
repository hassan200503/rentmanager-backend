package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.DisbursementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DisbursementRepositoryAdapter implements DisbursementRepository {

    private final DisbursementJpaRepository jpaRepository;

    @Override
    public Disbursement save(Disbursement disbursement) {
        DisbursementJpaEntity entity = toEntity(disbursement);
        DisbursementJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Disbursement> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Disbursement> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .map(this::toDomain);
    }

    @Override
    public List<Disbursement> findByStatusIn(List<DisbursementStatus> statuses) {
        return jpaRepository.findByStatusInOrderByCreatedAtAsc(statuses).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Disbursement> findByTenantIdAndStatusIn(UUID tenantId, List<DisbursementStatus> statuses) {
        return jpaRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, statuses).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Disbursement> findByStatusInAndRetryCountLessThan(List<DisbursementStatus> statuses, int maxRetries) {
        return jpaRepository.findByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(statuses, maxRetries).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Disbursement> findByStatusInAndCreatedAtBefore(List<DisbursementStatus> statuses, Instant cutoff) {
        return jpaRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(statuses, cutoff).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Disbursement> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    private DisbursementJpaEntity toEntity(Disbursement d) {
        return DisbursementJpaEntity.builder()
                .id(d.getId())
                .version(d.getVersion())
                .tenantId(d.getTenantId())
                .leaseId(d.getLeaseId())
                .ledgerEntryId(d.getLedgerEntryId())
                .amount(d.getAmount())
                .recipientPhone(d.getRecipientPhone())
                .recipientName(d.getRecipientName())
                .commandId(d.getCommandId())
                .status(d.getStatus())
                .mpesaTransactionId(d.getMpesaTransactionId())
                .mpesaConversationId(d.getMpesaConversationId())
                .mpesaOriginatorConversationId(d.getMpesaOriginatorConversationId())
                .failureReason(d.getFailureReason())
                .retryCount(d.getRetryCount())
                .requiresManualAttention(d.isRequiresManualAttention())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private Disbursement toDomain(DisbursementJpaEntity e) {
        return Disbursement.rehydrate(
                e.getId(), e.getTenantId(), e.getLeaseId(), e.getLedgerEntryId(),
                e.getAmount(), e.getRecipientPhone(), e.getRecipientName(),
                e.getCommandId(), e.getStatus(),
                e.getMpesaTransactionId(), e.getMpesaConversationId(),
                e.getMpesaOriginatorConversationId(), e.getFailureReason(),
                e.getRetryCount(), e.isRequiresManualAttention(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion()
        );
    }
}