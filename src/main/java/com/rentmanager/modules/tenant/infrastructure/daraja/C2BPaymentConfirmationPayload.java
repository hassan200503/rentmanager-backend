package com.rentmanager.modules.tenant.infrastructure.daraja;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Inbound C2B (Paybill) confirmation payload from Daraja - the delivery
 * mechanism for executed Ratiba standing-order payments. The merchant
 * registers the confirmation URL against the Paybill on the Daraja portal /
 * RegisterURL API; each standing-order execution arrives here with the
 * customer's account reference in {@code BillRefNumber}.
 */
public record C2BPaymentConfirmationPayload(
        @JsonProperty("TransID") String transId,
        @JsonProperty("TransAmount") BigDecimal transAmount,
        @JsonProperty("BillRefNumber") String billRefNumber,
        @JsonProperty("MSISDN") String msisdn,
        @JsonProperty("BusinessShortCode") String businessShortCode
) {
}
