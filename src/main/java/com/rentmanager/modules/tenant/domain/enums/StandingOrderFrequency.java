package com.rentmanager.modules.tenant.domain.enums;

/**
 * Ratiba standing-order frequency (Daraja "Frequency" codes: 1 = one-off,
 * 2 = daily, 3 = weekly, 4 = monthly, 5 = bi-monthly, 6 = quarterly,
 * 7 = half-year, 8 = yearly). Premium monthly billing uses MONTHLY only.
 */
public enum StandingOrderFrequency {
    MONTHLY;

    public String darajaCode() {
        return switch (this) {
            case MONTHLY -> "4";
        };
    }
}
