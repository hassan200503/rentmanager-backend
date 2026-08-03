package com.rentmanager.modules.tax.infrastructure.persistence.mapper;

import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.TaxInvoiceJpaEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class TaxInvoicePersistenceMapper {

    public TaxInvoiceJpaEntity toJpaEntity(TaxInvoice invoice) {
        if (invoice == null) {
            return null;
        }

        TaxInvoiceJpaEntity entity = new TaxInvoiceJpaEntity();
        entity.assignTenantIfUnset(invoice.getTenantId());
        entity.setRentTransactionId(invoice.getRentTransactionId());
        entity.setLedgerEntryId(invoice.getLedgerEntryId());
        entity.setLeaseId(invoice.getLeaseId());
        entity.setTenantProfileId(invoice.getTenantProfileId());
        entity.setLandlordKraPin(invoice.getLandlordKraPin());
        entity.setTenantKraPin(invoice.getTenantKraPin());
        entity.setPremisesType(invoice.getPremisesType());
        entity.setVatTreatment(invoice.getVatTreatment());
        entity.setStatus(invoice.getStatus());
        entity.setAmount(invoice.getAmount());
        entity.setExternalReference(invoice.getExternalReference());
        entity.setSource(invoice.getSource());
        entity.setOccurredAt(invoice.getOccurredAt());
        entity.setKraControlNumber(invoice.getKraControlNumber());
        entity.setQrCodeData(invoice.getQrCodeData());
        entity.setReceiptSignature(invoice.getReceiptSignature());
        entity.setSequentialReceiptNumber(invoice.getSequentialReceiptNumber());
        entity.setTransmittedAt(invoice.getTransmittedAt());
        entity.setAttemptCount(invoice.getAttemptCount());
        entity.setNextAttemptAt(invoice.getNextAttemptAt());
        entity.setLastError(invoice.getLastError());

        setField(entity, "id", invoice.getId());
        setField(entity, "createdAt", invoice.getCreatedAt());
        setField(entity, "updatedAt", invoice.getUpdatedAt());
        setField(entity, "version", invoice.getVersion());
        return entity;
    }

    public TaxInvoice toDomain(TaxInvoiceJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return TaxInvoice.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getRentTransactionId(),
                entity.getLedgerEntryId(),
                entity.getLeaseId(),
                entity.getTenantProfileId(),
                entity.getLandlordKraPin(),
                entity.getTenantKraPin(),
                entity.getPremisesType(),
                entity.getVatTreatment(),
                entity.getStatus(),
                entity.getAmount(),
                entity.getExternalReference(),
                entity.getSource(),
                entity.getOccurredAt(),
                entity.getKraControlNumber(),
                entity.getQrCodeData(),
                entity.getReceiptSignature(),
                entity.getSequentialReceiptNumber(),
                entity.getTransmittedAt(),
                entity.getAttemptCount(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map field: " + fieldName, ex);
        }
    }
}
