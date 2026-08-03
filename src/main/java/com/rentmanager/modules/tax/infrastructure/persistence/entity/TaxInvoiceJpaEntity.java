package com.rentmanager.modules.tax.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tax_invoices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tax_invoice_per_transaction",
                        columnNames = {"tenant_id", "rent_transaction_id"}
                )
        },
        indexes = {
                @Index(name = "idx_tax_invoices_due", columnList = "status, attempt_count, next_attempt_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TaxInvoiceJpaEntity extends BaseTenantEntity {

    @Column(name = "rent_transaction_id", nullable = false)
    private UUID rentTransactionId;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "tenant_profile_id")
    private UUID tenantProfileId;

    @Column(name = "landlord_kra_pin")
    private String landlordKraPin;

    @Column(name = "tenant_kra_pin")
    private String tenantKraPin;

    @Enumerated(EnumType.STRING)
    @Column(name = "premises_type", nullable = false)
    private PremisesType premisesType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_treatment", nullable = false)
    private VatTreatment vatTreatment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaxInvoiceStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "source")
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "kra_control_number")
    private String kraControlNumber;

    @Column(name = "qr_code_data")
    private String qrCodeData;

    @Column(name = "receipt_signature")
    private String receiptSignature;

    @Column(name = "sequential_receipt_number")
    private String sequentialReceiptNumber;

    @Column(name = "transmitted_at")
    private LocalDateTime transmittedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;
}