package com.rentmanager.modules.notification.push.domain;

/** Push categories a person can switch on or off (V100). Default: on. */
public enum PushCategory {
    /** A rent payment was recorded on the renter's account. */
    RENT_PAYMENTS,
    /** Maintenance requests: new ones for landlords, updates for renters. */
    MAINTENANCE
}
