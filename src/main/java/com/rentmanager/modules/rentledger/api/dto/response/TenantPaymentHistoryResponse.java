package com.rentmanager.modules.rentledger.api.dto.response;

import java.util.List;

public record TenantPaymentHistoryResponse(
        List<TenantDashboardResponse.PaymentHistoryItem> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last,
        boolean empty
) {}
