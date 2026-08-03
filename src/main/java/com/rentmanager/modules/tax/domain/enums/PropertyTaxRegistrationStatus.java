package com.rentmanager.modules.tax.domain.enums;

/**
 * Lifecycle of a property's eRITS registration.
 *
 * <p>PENDING — created, awaiting the registration transmission.
 * READY_FOR_MANUAL — handed over to the landlord to register manually
 * (when the landlord lacks a KRA pin or the eRITS tenant pin is
 * unconfirmed, item A1). TRANSMITTED — submission delivered to KRA,
 * awaiting acceptance. ACCEPTED — KRA registered the property
 * (kr_property_registration_id populated). REJECTED — KRA refused;
 * last_error carries the reason.
 */
public enum PropertyTaxRegistrationStatus {

    PENDING,
    READY_FOR_MANUAL,
    TRANSMITTED,
    ACCEPTED,
    REJECTED
}
