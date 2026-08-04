package com.rentmanager.modules.property.domain.enums;

/**
 * RESIDENTIAL/COMMERCIAL/MIXED_USE classification of a property, used for the
 * KRA tax branch (eTIMS / eRITS / MRI).
 *
 * <p>RESIDENTIAL — always VAT-exempt rent (First Schedule Part II para 8,
 * VAT Act 2013); the only premises included in the landlord's Monthly Rental
 * Income (MRI) aggregation.
 *
 * <p>COMMERCIAL — never part of the MRI regime; a 16% VAT branch applies once
 * the landlord is VAT-registered and the tax-advisor sign-off is complete.
 *
 * <p>MIXED_USE — a single building let to both residential and commercial
 * tenants. Under the Income Tax Act (mixed-use FAQ) the income must be split
 * between the MRI portion (residential) and the standard/16% VAT branch
 * (commercial). It can NEVER be auto-derived from {@link PropertyType} — it
 * only exists through an explicit, audited override (an override reason is
 * mandatory) <em>until unit-level classification (phase 2) can apportion the
 * rental income precisely</em>. Until then it is fail-closed: excluded from
 * the MRI aggregation and treated as VAT-exempt on invoicing, so no regime is
 * silently misapplied.
 *
 * <p>Single source of truth at persist time is the {@code properties}
 * {@code premises_type} column. When a property is created without an explicit
 * premises type, it is derived from {@link PropertyType} — {@link
 * #fromPropertyType} never returns MIXED_USE.
 */
public enum PremisesType {

    RESIDENTIAL,
    COMMERCIAL,
    MIXED_USE;

    /**
     * Auto-derivation rule used at creation/type-change: COMMERCIAL/OFFICE/
     * WAREHOUSE -> COMMERCIAL, everything else -> RESIDENTIAL. Returns {@code
     * null} for a null input and NEVER returns MIXED_USE — mixed-use requires
     * an explicit audited override.
     */
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
