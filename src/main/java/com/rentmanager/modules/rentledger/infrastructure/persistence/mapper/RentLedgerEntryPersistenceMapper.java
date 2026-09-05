package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RentLedgerEntryPersistenceMapper {

    public RentLedgerEntryJpaEntity toJpaEntity(RentLedgerEntry entry) {
        if (entry == null) return null;

        RentLedgerEntryJpaEntity jpa = new RentLedgerEntryJpaEntity();
        jpa.setId(entry.getId());
        jpa.assignTenantIfUnset(entry.getTenantId()); // see note below
        jpa.setVersion(entry.getVersion());
        jpa.setLeaseId(entry.getLeaseId());
        jpa.setUnitId(entry.getUnitId());
        jpa.setTenantProfileId(entry.getTenantProfileId());
        jpa.setBillingPeriodStart(entry.getBillingPeriodStart());
        jpa.setBillingPeriodEnd(entry.getBillingPeriodEnd());
        jpa.setDueDate(entry.getDueDate());
        jpa.setAmountDue(entry.getAmountDue());
        jpa.setAmountPaid(entry.getAmountPaid());
        jpa.setStatus(entry.getStatus());
        jpa.setProrated(entry.isProrated());
        jpa.setCurrency(entry.getCurrency());
        return jpa;
    }

    public RentLedgerEntry toDomain(RentLedgerEntryJpaEntity jpa) {
        if (jpa == null) return null;

        return RentLedgerEntry.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getLeaseId(),
                jpa.getUnitId(),
                jpa.getTenantProfileId(),
                jpa.getBillingPeriodStart(),
                jpa.getBillingPeriodEnd(),
                jpa.getDueDate(),
                jpa.getAmountDue(),
                jpa.getAmountPaid(),
                jpa.getStatus(),
                jpa.isProrated(),
                jpa.getCurrency(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}