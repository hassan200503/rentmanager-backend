package com.rentmanager.contract.lease.response;

import java.util.List;

public record LeaseListResponse(
        List<LeaseSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}