package com.rentmanager.modules.tenant.infrastructure.persistence.specification;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantSubscriptionEntity;

import java.time.Instant;

public class TenantSubscriptionSpecification {

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION CAN BE ACTIVATED
    // ------------------------------------------------------------
    public static boolean canActivate(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            throw new IllegalArgumentException("Subscription cannot be null");
        }

        return subscription.getStatus().equals("TRIAL")
                || subscription.getStatus().equals("SUSPENDED")
                || subscription.getStatus().equals("EXPIRED");
    }

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION CAN BE SUSPENDED
    // ------------------------------------------------------------
    public static boolean canSuspend(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            throw new IllegalArgumentException("Subscription cannot be null");
        }

        return subscription.getStatus().equals("ACTIVE");
    }

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION CAN BE CANCELLED
    // ------------------------------------------------------------
    public static boolean canCancel(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            throw new IllegalArgumentException("Subscription cannot be null");
        }

        return !subscription.getStatus().equals("CANCELLED");
    }

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION IS ACTIVE
    // ------------------------------------------------------------
    public static boolean isActive(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            return false;
        }

        return subscription.getStatus().equals("ACTIVE");
    }

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION IS IN TRIAL
    // ------------------------------------------------------------
    public static boolean isInTrial(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            return false;
        }

        return subscription.getStatus().equals("TRIAL");
    }

    // ------------------------------------------------------------
    // VALID: SUBSCRIPTION IS EXPIRED
    // ------------------------------------------------------------
    public static boolean isExpired(TenantSubscriptionEntity subscription) {

        if (subscription == null) {
            return true;
        }

        Instant now = Instant.now();

        return subscription.getEndDate() != null
                && subscription.getEndDate().isBefore(now);
    }

    // ------------------------------------------------------------
    // VALID: CAN CREATE PROPERTY (SAAS LIMIT RULE)
    // ------------------------------------------------------------
    public static boolean canCreateProperty(TenantSubscriptionEntity subscription, long currentPropertyCount) {

        if (!isActive(subscription)) {
            return false;
        }

        if (subscription.getMaxProperties() == null) {
            return true; // unlimited
        }

        return currentPropertyCount < subscription.getMaxProperties();
    }

    // ------------------------------------------------------------
    // VALID: CAN CREATE UNIT
    // ------------------------------------------------------------
    public static boolean canCreateUnit(TenantSubscriptionEntity subscription, long currentUnitCount) {

        if (!isActive(subscription)) {
            return false;
        }

        if (subscription.getMaxUnits() == null) {
            return true;
        }

        return currentUnitCount < subscription.getMaxUnits();
    }

    // ------------------------------------------------------------
    // VALID: CAN ADD USER
    // ------------------------------------------------------------
    public static boolean canAddUser(TenantSubscriptionEntity subscription, long currentUserCount) {

        if (!isActive(subscription)) {
            return false;
        }

        if (subscription.getMaxUsers() == null) {
            return true;
        }

        return currentUserCount < subscription.getMaxUsers();
    }
}