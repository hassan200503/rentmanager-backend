package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;

/**
 * Read-model projection for STK payment-request counts grouped by status.
 */
public record PaymentRequestStatusCount(RentPaymentRequestStatus status, long count) {
}
