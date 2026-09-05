package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.model.RentReminder;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.RentReminderPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentReminderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RentReminderRepositoryAdapter implements RentReminderRepository {

    private final RentReminderJpaRepository jpaRepository;

    /**
     * {@code saveAndFlush}, not {@code save}, so that a duplicate raises
     * {@code DataIntegrityViolationException} at this call rather than
     * silently at commit time — by which point the caller would already have
     * enqueued the outbox delivery and believe it succeeded.
     */
    @Override
    public void save(RentReminder reminder) {
        jpaRepository.saveAndFlush(RentReminderPersistenceMapper.toEntity(reminder));
    }

    @Override
    public List<RentReminder> findByEntry(UUID tenantId, UUID rentLedgerEntryId) {
        return jpaRepository.findByTenantIdAndRentLedgerEntryId(tenantId, rentLedgerEntryId)
                .stream()
                .map(RentReminderPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<RentReminder> findByTenantAndDueDateBetween(
            UUID tenantId, LocalDate from, LocalDate to) {
        return jpaRepository
                .findByTenantIdAndDueDateBetweenOrderBySentAtDesc(tenantId, from, to)
                .stream()
                .map(RentReminderPersistenceMapper::toDomain)
                .toList();
    }
}
