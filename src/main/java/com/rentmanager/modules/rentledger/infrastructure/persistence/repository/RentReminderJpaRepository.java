package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentReminderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RentReminderJpaRepository extends JpaRepository<RentReminderJpaEntity, UUID> {

    List<RentReminderJpaEntity> findByTenantIdAndRentLedgerEntryId(
            UUID tenantId, UUID rentLedgerEntryId);

    List<RentReminderJpaEntity> findByTenantIdAndDueDateBetweenOrderBySentAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);
}
