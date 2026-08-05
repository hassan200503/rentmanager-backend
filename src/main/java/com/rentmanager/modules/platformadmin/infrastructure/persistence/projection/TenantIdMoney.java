package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-model projection for money totals grouped by landlord (tenant) org id.
 * {@code amount} is gross collection; {@code commissionAmount} is the
 * platform's retained commission (may be null when no commission applies).
 */
public record TenantIdMoney(UUID tenantId, BigDecimal amount, BigDecimal commissionAmount) {
}
