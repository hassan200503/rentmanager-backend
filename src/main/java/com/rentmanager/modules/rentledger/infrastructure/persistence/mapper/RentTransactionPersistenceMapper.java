package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RentTransactionPersistenceMapper {

    public RentTransactionJpaEntity toJpaEntity(RentTransaction transaction) {
        if (transaction == null) return null;

        RentTransactionJpaEntity jpa = new RentTransactionJpaEntity();
        jpa.setId(transaction.getId());
        jpa.assignTenantIfUnset(transaction.getTenantId());
        jpa.setVersion(transaction.getVersion());
        jpa.setLedgerEntryId(transaction.getLedgerEntryId());
        jpa.setLeaseId(transaction.getLeaseId());
        jpa.setType(transaction.getType());
        jpa.setAmount(transaction.getAmount());
        jpa.setExternalReference(transaction.getExternalReference());
        jpa.setSource(transaction.getSource());
        jpa.setRecordedBy(transaction.getRecordedBy());
        jpa.setOccurredAt(transaction.getOccurredAt());
        jpa.setCommissionRatePercent(transaction.getCommissionRatePercent());
        jpa.setCommissionAmount(transaction.getCommissionAmount());
        jpa.setNetAmount(transaction.getNetAmount());
        return jpa;
    }

    public RentTransaction toDomain(RentTransactionJpaEntity jpa) {
        if (jpa == null) return null;

        return RentTransaction.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getLedgerEntryId(),
                jpa.getLeaseId(),
                jpa.getType(),
                jpa.getAmount(),
                jpa.getExternalReference(),
                jpa.getSource(),
                jpa.getRecordedBy(),
                jpa.getOccurredAt(),
                jpa.getCommissionRatePercent(),
                jpa.getCommissionAmount(),
                jpa.getNetAmount(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}