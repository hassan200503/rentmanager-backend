package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.domain.base.AggregateRoot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Disbursement extends AggregateRoot {

    private static final String DEFAULT_CURRENCY = "KES";

    private UUID leaseId;
    private UUID ledgerEntryId;
    private BigDecimal amount;
    private String recipientPhone;
    private String recipientName;
    private String commandId;
    private DisbursementStatus status;
    private String mpesaTransactionId;
    private String mpesaConversationId;
    private String mpesaOriginatorConversationId;
    private String failureReason;
    private int retryCount;
    private boolean requiresManualAttention;
    private Instant createdAt;
    private Instant updatedAt;
    private String currency;

    private Disbursement() {}

    /**
     * Shorter overload defaulting {@code currency} to {@link #DEFAULT_CURRENCY}
     * — see the currency-explicit overload below, used by real disbursement
     * call sites with the landlord's tenant.currency.
     */
    public static Disbursement create(
            UUID tenantId,
            UUID leaseId,
            UUID ledgerEntryId,
            BigDecimal amount,
            String recipientPhone,
            String recipientName,
            String commandId
    ) {
        return create(tenantId, leaseId, ledgerEntryId, amount, recipientPhone, recipientName, commandId, DEFAULT_CURRENCY);
    }

    public static Disbursement create(
            UUID tenantId,
            UUID leaseId,
            UUID ledgerEntryId,
            BigDecimal amount,
            String recipientPhone,
            String recipientName,
            String commandId,
            String currency
    ) {
        Disbursement d = new Disbursement();
        d.setId(UUID.randomUUID());
        d.assignTenant(tenantId);
        d.leaseId = leaseId;
        d.ledgerEntryId = ledgerEntryId;
        d.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        d.recipientPhone = recipientPhone;
        d.recipientName = recipientName;
        d.commandId = commandId;
        d.status = DisbursementStatus.INITIATED;
        d.retryCount = 0;
        d.requiresManualAttention = false;
        d.createdAt = Instant.now();
        d.updatedAt = Instant.now();
        d.currency = currency != null ? currency : DEFAULT_CURRENCY;
        return d;
    }

    public void markPending(String originatorConversationId) {
        this.mpesaOriginatorConversationId = originatorConversationId;
        this.status = DisbursementStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markSuccess(String transactionId, String conversationId) {
        this.mpesaTransactionId = transactionId;
        this.mpesaConversationId = conversationId;
        this.status = DisbursementStatus.SUCCESS;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason, String conversationId) {
        this.mpesaConversationId = conversationId;
        this.failureReason = reason;
        this.status = DisbursementStatus.FAILED;
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public void markRequiresManualAttention() {
        this.requiresManualAttention = true;
        this.updatedAt = Instant.now();
    }

    public static Disbursement rehydrate(
            UUID id, UUID tenantId, UUID leaseId, UUID ledgerEntryId,
            BigDecimal amount, String recipientPhone, String recipientName,
            String commandId, DisbursementStatus status,
            String mpesaTransactionId, String mpesaConversationId,
            String mpesaOriginatorConversationId, String failureReason,
            int retryCount, boolean requiresManualAttention,
            Instant createdAt, Instant updatedAt,
            String currency,
            Long version
    ) {
        Disbursement d = new Disbursement();
        d.setId(id);
        d.assignTenant(tenantId);
        d.leaseId = leaseId;
        d.ledgerEntryId = ledgerEntryId;
        d.amount = amount;
        d.recipientPhone = recipientPhone;
        d.recipientName = recipientName;
        d.commandId = commandId;
        d.status = status;
        d.mpesaTransactionId = mpesaTransactionId;
        d.mpesaConversationId = mpesaConversationId;
        d.mpesaOriginatorConversationId = mpesaOriginatorConversationId;
        d.failureReason = failureReason;
        d.retryCount = retryCount;
        d.requiresManualAttention = requiresManualAttention;
        d.createdAt = createdAt;
        d.updatedAt = updatedAt;
        d.currency = currency != null ? currency : DEFAULT_CURRENCY;
        d.setVersion(version);
        return d;
    }

    public UUID getLeaseId() { return leaseId; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public BigDecimal getAmount() { return amount; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientName() { return recipientName; }
    public String getCommandId() { return commandId; }
    public DisbursementStatus getStatus() { return status; }
    public String getMpesaTransactionId() { return mpesaTransactionId; }
    public String getMpesaConversationId() { return mpesaConversationId; }
    public String getMpesaOriginatorConversationId() { return mpesaOriginatorConversationId; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public boolean isRequiresManualAttention() { return requiresManualAttention; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCurrency() { return currency; }
}