package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Extends {@code BaseTenantEntity} (id, tenantId, version, createdAt,
 * updatedAt all inherited) to match this module's own established
 * convention — see {@code RentLedgerEntryJpaEntity} and
 * {@code RentTransactionJpaEntity} — rather than PaymentIntentJpaEntity's
 * flat, self-declared-fields style from the reservation module. The
 * domain aggregate {@code RentPaymentRequest} mirrors PaymentIntent's
 * *shape* (no updatedAt field there); updatedAt exists here purely as
 * inherited audit bookkeeping and is not read back into the domain object,
 * same as PaymentIntentJpaEntity.createdAt is carried but not authoritative.
 *
 * The partial unique index on mpesa_checkout_request_id (NULL-safe, since
 * it isn't known until the STK push call returns) lives only in the
 * migration — @UniqueConstraint on @Table can't express a WHERE clause,
 * same reasoning already applied to RentTransactionJpaEntity's own partial
 * unique index.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rent_payment_requests")
public class RentPaymentRequestJpaEntity extends BaseTenantEntity {

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "rent_ledger_entry_id", nullable = false)
    private UUID rentLedgerEntryId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "mpesa_checkout_request_id")
    private String mpesaCheckoutRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RentPaymentRequestStatus status;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;
}