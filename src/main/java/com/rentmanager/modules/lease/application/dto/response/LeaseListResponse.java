package com.rentmanager.modules.lease.application.dto.response;

import java.util.List;

public record LeaseListResponse(
        List<LeaseSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}