package com.rentmanager.modules.property.domain.enums;

/**
 * RESIDENTIAL/COMMERCIAL classification of a property, used for the KRA tax
 * branch (eTIMS / eRITS / MRI).
 *
 * <p>RESIDENTIAL — always VAT-exempt rent (First Schedule Part II para 8,
 * VAT Act 2013); the only premises included in the landlord's Monthly Rental
 * Income (MRI) aggregation.
 *
 * <p>COMMERCIAL — never part of the MRI regime; a 16% VAT branch applies once
 * the landlord is VAT-registered and the tax-advisor sign-off is complete.
 *
 * <p>Single source of truth at persist time is the {@code properties}
 * {@code premises_type} column. When a property is created without an explicit
 * premises type, it is derived from {@link PropertyType}.
 */
public enum PremisesType {

    RESIDENTIAL,
    COMMERCIAL;

    public static PremisesType fromPropertyType(PropertyType propertyType) {
        if (propertyType == null) {
            return null;
        }
        return switch (propertyType) {
            case COMMERCIAL, OFFICE, WAREHOUSE -> COMMERCIAL;
            default -> RESIDENTIAL;
        };
    }
}
