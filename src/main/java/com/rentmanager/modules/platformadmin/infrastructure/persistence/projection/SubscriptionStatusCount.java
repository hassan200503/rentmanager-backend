package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;

/**
 * Read-model projection for the platform admin overview:
 * tenant counts grouped by subscription status.
 */
public record SubscriptionStatusCount(SubscriptionStatus status, long count) {
}
