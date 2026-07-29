package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disbursements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "command_id", nullable = false, length = 50)
    private String commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisbursementStatus status;

    @Column(name = "mpesa_transaction_id", length = 100)
    private String mpesaTransactionId;

    @Column(name = "mpesa_conversation_id", length = 100)
    private String mpesaConversationId;

    @Column(name = "mpesa_originator_conversation_id", length = 100)
    private String mpesaOriginatorConversationId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "requires_manual_attention", nullable = false)
    private boolean requiresManualAttention;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}