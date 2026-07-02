package com.rentmanager.modules.reservation.infrastructure.persistence.entity;

import com.rentmanager.modules.reservation.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reservations")
public class ReservationJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "national_id", nullable = false)
    private String nationalId;

    @Column(name = "mpesa_phone", nullable = false)
    private String mpesaPhone;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    @Column(name = "clerk_user_id")
    private String clerkUserId;

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(name = "fulfillment_failure_reason", length = 2000)
    private String fulfillmentFailureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}