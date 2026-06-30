package com.rentmanager.modules.tenant.infrastructure.persistence.specification;



import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;

import java.math.BigDecimal;

public class SubscriptionPlanSpecification {

    // ------------------------------------------------------------
    // CAN CREATE PLAN
    // ------------------------------------------------------------
    public static boolean canCreate(SubscriptionPlanEntity plan) {

        if (plan == null) {
            throw new IllegalArgumentException("Plan cannot be null");
        }

        if (plan.getTenantId() == null) {
            return false;
        }

        if (plan.getName() == null || plan.getName().isBlank()) {
            return false;
        }

        if (plan.getPlanCode() == null || plan.getPlanCode().isBlank()) {
            return false;
        }

        if (plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        return true;
    }

    // ------------------------------------------------------------
    // CAN ACTIVATE PLAN
    // ------------------------------------------------------------
    public static boolean canActivate(SubscriptionPlanEntity plan) {

        if (plan == null) {
            throw new IllegalArgumentException("Plan cannot be null");
        }

        return !plan.isActive();
    }

    // ------------------------------------------------------------
    // CAN DEACTIVATE PLAN
    // ------------------------------------------------------------
    public static boolean canDeactivate(SubscriptionPlanEntity plan) {

        if (plan == null) {
            throw new IllegalArgumentException("Plan cannot be null");
        }

        return plan.isActive();
    }

    // ------------------------------------------------------------
    // VALID PLAN LIMITS
    // ------------------------------------------------------------
    public static boolean hasValidLimits(SubscriptionPlanEntity plan) {

        if (plan == null) {
            return false;
        }

        return plan.getMaxProperties() == null || plan.getMaxProperties() >= 0
                && plan.getMaxUnits() == null || plan.getMaxUnits() >= 0
                && plan.getMaxUsers() == null || plan.getMaxUsers() >= 0;
    }

    // ------------------------------------------------------------
    // CAN UPGRADE PLAN
    // ------------------------------------------------------------
    public static boolean canUpgrade(SubscriptionPlanEntity current, SubscriptionPlanEntity target) {

        if (current == null || target == null) {
            return false;
        }

        return target.getPrice().compareTo(current.getPrice()) > 0;
    }

    // ------------------------------------------------------------
    // CAN DOWNGRADE PLAN
    // ------------------------------------------------------------
    public static boolean canDowngrade(SubscriptionPlanEntity current, SubscriptionPlanEntity target) {

        if (current == null || target == null) {
            return false;
        }

        return target.getPrice().compareTo(current.getPrice()) < 0;
    }
}