package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TenantPaymentReceiptResponse(
        UUID transactionId,
        String receiptNumber,
        LocalDateTime paymentDate,
        BigDecimal amount,
        String mpesaTransactionId,
        String tenantName,
        String tenantPhone,
        String unitNumber,
        String propertyName,
        String billingPeriodStart,
        String billingPeriodEnd,
        BigDecimal balanceAfterPayment,
        String eTimsInvoiceNumber,
        String eTimsQrCodeUrl
) {}
