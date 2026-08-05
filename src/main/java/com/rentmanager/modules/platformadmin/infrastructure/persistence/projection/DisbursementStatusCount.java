package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;

/**
 * Read-model projection for disbursement counts grouped by status.
 */
public record DisbursementStatusCount(DisbursementStatus status, long count) {
}
