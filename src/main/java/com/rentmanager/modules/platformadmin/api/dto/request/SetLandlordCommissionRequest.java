package com.rentmanager.modules.platformadmin.api.dto.request;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/v1/admin/landlords/{id}/commission}. Rate is a
 * percentage (e.g. 5.00 = 5%). Must be within [0, 100]; validated in
 * PlatformAdminCommissionService.
 */
public record SetLandlordCommissionRequest(BigDecimal ratePercent) {
}