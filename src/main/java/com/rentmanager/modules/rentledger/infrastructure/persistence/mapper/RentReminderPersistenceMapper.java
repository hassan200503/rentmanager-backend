package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.RentReminder;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentReminderJpaEntity;

public final class RentReminderPersistenceMapper {

    private RentReminderPersistenceMapper() {
        // utility class — not instantiable
    }

    public static RentReminderJpaEntity toEntity(RentReminder reminder) {
        RentReminderJpaEntity entity = new RentReminderJpaEntity();
        entity.setId(reminder.getId());
        entity.setTenantId(reminder.getTenantId());
        entity.setRentLedgerEntryId(reminder.getRentLedgerEntryId());
        entity.setLeaseId(reminder.getLeaseId());
        entity.setMilestone(reminder.getMilestone());
        entity.setAudience(reminder.getAudience());
        entity.setChannel(reminder.getChannel());
        entity.setRecipientMasked(reminder.getRecipientMasked());
        entity.setBalanceOwedSnapshot(reminder.getBalanceOwedSnapshot());
        entity.setCurrency(reminder.getCurrency());
        entity.setDueDate(reminder.getDueDate());
        entity.setNotificationDeliveryId(reminder.getNotificationDeliveryId());
        entity.setSentAt(reminder.getSentAt());
        entity.setCreatedAt(reminder.getCreatedAt());
        return entity;
    }

    public static RentReminder toDomain(RentReminderJpaEntity entity) {
        return RentReminder.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getRentLedgerEntryId(),
                entity.getLeaseId(),
                entity.getMilestone(),
                entity.getAudience(),
                entity.getChannel(),
                entity.getRecipientMasked(),
                entity.getBalanceOwedSnapshot(),
                entity.getCurrency(),
                entity.getDueDate(),
                entity.getNotificationDeliveryId(),
                entity.getSentAt(),
                entity.getCreatedAt()
        );
    }
}
