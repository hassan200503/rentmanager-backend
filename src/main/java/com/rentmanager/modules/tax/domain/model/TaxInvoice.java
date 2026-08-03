package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * eTIMS tax invoice for a single rent transaction. One invoice per
 * (tenant, rent transaction) — the repository's unique key makes
 * re-delivered payment events idempotent.
 *
 * <p>Transmission lifecycle (mirrors the disbursement retry pattern):
 * <ul>
 *   <li>create() → status PENDING, attemptCount 0, nextAttemptAt = now.</li>
 *   <li>Transmission success → TRANSMITTED with KRA receipt details.</li>
 *   <li>Transmission failure → FAILED, attemptCount++, nextAttemptAt =
 *       now + 30m * attemptCount (exponential backoff), until
 *       {@code maxAttempts} is reached — after which nextAttemptAt is
 *       cleared and the sweeper stops picking the invoice up.</li>
 *   <li>SELF_FILED is terminal and never auto-retried.</li>
 * </ul>
 */
public class TaxInvoice extends AggregateRoot {

    private static final int BACKOFF_MINUTES = 30;

    private UUID rentTransactionId;
    private UUID ledgerEntryId;
    private UUID leaseId;
    private UUID tenantProfileId;
    private String landlordKraPin;
    private String tenantKraPin;
    private PremisesType premisesType;
    private VatTreatment vatTreatment;
    private TaxInvoiceStatus status;
    private BigDecimal amount;
    private String externalReference;
    private String source;
    private LocalDateTime occurredAt;
    private String kraControlNumber;
    private String qrCodeData;
    private String receiptSignature;
    private String sequentialReceiptNumber;
    private LocalDateTime transmittedAt;
    private int attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    protected TaxInvoice() {
    }

    public static TaxInvoice create(
            UUID landlordTenantId,
            UUID rentTransactionId,
            UUID ledgerEntryId,
            UUID leaseId,
            UUID tenantProfileId,
            String landlordKraPin,
            String tenantKraPin,
            PremisesType premisesType,
            VatTreatment vatTreatment,
            BigDecimal amount,
            String externalReference,
            String source,
            LocalDateTime occurredAt
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (rentTransactionId == null) {
            throw new IllegalArgumentException("rentTransactionId is required");
        }
        if (ledgerEntryId == null) {
            throw new IllegalArgumentException("ledgerEntryId is required");
        }
        if (leaseId == null) {
            throw new IllegalArgumentException("leaseId is required");
        }
        if (premisesType == null) {
            throw new IllegalArgumentException("premisesType is required");
        }
        if (vatTreatment == null) {
            throw new IllegalArgumentException("vatTreatment is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }

        TaxInvoice invoice = new TaxInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.assignTenant(landlordTenantId);
        invoice.rentTransactionId = rentTransactionId;
        invoice.ledgerEntryId = ledgerEntryId;
        invoice.leaseId = leaseId;
        invoice.tenantProfileId = tenantProfileId;
        invoice.landlordKraPin = landlordKraPin;
        invoice.tenantKraPin = tenantKraPin;
        invoice.premisesType = premisesType;
        invoice.vatTreatment = vatTreatment;
        invoice.status = TaxInvoiceStatus.PENDING;
        invoice.amount = amount;
        invoice.externalReference = externalReference;
        invoice.source = source;
        invoice.occurredAt = occurredAt;
        invoice.attemptCount = 0;
        invoice.nextAttemptAt = LocalDateTime.now();
        return invoice;
    }

    /**
     * Marks the invoice TRANSMITTED with KRA's acknowledgement details.
     */
    public void markTransmitted(
            String kraControlNumber,
            String qrCodeData,
            String receiptSignature,
            String sequentialReceiptNumber
    ) {
        if (status == TaxInvoiceStatus.TRANSMITTED || status == TaxInvoiceStatus.SELF_FILED) {
            return;
        }
        this.status = TaxInvoiceStatus.TRANSMITTED;
        this.kraControlNumber = kraControlNumber;
        this.qrCodeData = qrCodeData;
        this.receiptSignature = receiptSignature;
        this.sequentialReceiptNumber = sequentialReceiptNumber;
        this.transmittedAt = LocalDateTime.now();
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Records a failed transmission attempt and schedules the next one
     * (exponential backoff). Once {@code maxAttempts} are exhausted,
     * nextAttemptAt is cleared so the sweeper stops retrying.
     */
    public void markTransmissionFailure(String error, int maxAttempts) {
        if (status == TaxInvoiceStatus.TRANSMITTED || status == TaxInvoiceStatus.SELF_FILED) {
            return;
        }
        this.status = TaxInvoiceStatus.FAILED;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.attemptCount++;
        if (this.attemptCount < maxAttempts) {
            this.nextAttemptAt = LocalDateTime.now().plusMinutes(BACKOFF_MINUTES * (long) this.attemptCount);
        } else {
            this.nextAttemptAt = null;
        }
    }

    /**
     * Landlord filed this invoice themselves — terminal state.
     */
    public void markSelfFiled() {
        this.status = TaxInvoiceStatus.SELF_FILED;
        this.nextAttemptAt = null;
    }

    public boolean isTransmissionCandidate(LocalDateTime now, int maxAttempts) {
        if (status != TaxInvoiceStatus.PENDING && status != TaxInvoiceStatus.FAILED) {
            return false;
        }
        if (attemptCount >= maxAttempts || nextAttemptAt == null) {
            return false;
        }
        return !nextAttemptAt.isAfter(now);
    }

    public static TaxInvoice rehydrate(
            UUID id,
            UUID tenantId,
            UUID rentTransactionId,
            UUID ledgerEntryId,
            UUID leaseId,
            UUID tenantProfileId,
            String landlordKraPin,
            String tenantKraPin,
            PremisesType premisesType,
            VatTreatment vatTreatment,
            TaxInvoiceStatus status,
            BigDecimal amount,
            String externalReference,
            String source,
            LocalDateTime occurredAt,
            String kraControlNumber,
            String qrCodeData,
            String receiptSignature,
            String sequentialReceiptNumber,
            LocalDateTime transmittedAt,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        TaxInvoice invoice = new TaxInvoice();
        invoice.setId(id);
        invoice.restoreTenantId(tenantId);
        invoice.rentTransactionId = rentTransactionId;
        invoice.ledgerEntryId = ledgerEntryId;
        invoice.leaseId = leaseId;
        invoice.tenantProfileId = tenantProfileId;
        invoice.landlordKraPin = landlordKraPin;
        invoice.tenantKraPin = tenantKraPin;
        invoice.premisesType = premisesType;
        invoice.vatTreatment = vatTreatment;
        invoice.status = status;
        invoice.amount = amount;
        invoice.externalReference = externalReference;
        invoice.source = source;
        invoice.occurredAt = occurredAt;
        invoice.kraControlNumber = kraControlNumber;
        invoice.qrCodeData = qrCodeData;
        invoice.receiptSignature = receiptSignature;
        invoice.sequentialReceiptNumber = sequentialReceiptNumber;
        invoice.transmittedAt = transmittedAt;
        invoice.attemptCount = attemptCount;
        invoice.nextAttemptAt = nextAttemptAt;
        invoice.lastError = lastError;
        invoice.version = version;
        invoice.createdAt = createdAt;
        invoice.updatedAt = updatedAt;
        return invoice;
    }

    public UUID getRentTransactionId() { return rentTransactionId; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public UUID getLeaseId() { return leaseId; }
    public UUID getTenantProfileId() { return tenantProfileId; }
    public String getLandlordKraPin() { return landlordKraPin; }
    public String getTenantKraPin() { return tenantKraPin; }
    public PremisesType getPremisesType() { return premisesType; }
    public VatTreatment getVatTreatment() { return vatTreatment; }
    public TaxInvoiceStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getExternalReference() { return externalReference; }
    public String getSource() { return source; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getKraControlNumber() { return kraControlNumber; }
    public String getQrCodeData() { return qrCodeData; }
    public String getReceiptSignature() { return receiptSignature; }
    public String getSequentialReceiptNumber() { return sequentialReceiptNumber; }
    public LocalDateTime getTransmittedAt() { return transmittedAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
