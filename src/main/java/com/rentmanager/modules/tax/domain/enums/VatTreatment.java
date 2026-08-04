package com.rentmanager.modules.tax.domain.enums;

import com.rentmanager.modules.property.domain.enums.PremisesType;

/**
 * VAT treatment of a rental invoice.
 *
 * <p>VAT_EXEMPT — residential rent (First Schedule Part II para 8 of the
 * VAT Act 2013). Always the treatment for RESIDENTIAL premises.
 *
 * <p>STANDARD_RATED — commercial rent charged 16% VAT, ONLY when the
 * landlord is VAT-registered. The 16% branch stays dormant until the tax
 * advisor sign-off (brief item A1) completes.
 *
 * <p>ZERO_RATED — reserved; used when a zero-rated supply is ever
 * applicable to rent (none today).
 */
public enum VatTreatment {

    STANDARD_RATED,
    ZERO_RATED,
    VAT_EXEMPT;

    /**
     * Residential premises are always VAT-exempt. Commercial premises are
     * only STANDARD_RATED once the landlord's VAT registration is
     * confirmed — fail-closed to VAT_EXEMPT until then (the 16% branch
     * additionally awaits the A1 advisor sign-off).
     *
     * <p>MIXED_USE is fail-closed to VAT_EXEMPT: without unit-level income
     * apportionment (phase 2) the commercial portion cannot be isolated, so
     * no VAT is charged on the whole. This mirrors the audit intent of the
     * source regime — never guess between the two branches for an
     * unsplit mixed building.
     */
    public static VatTreatment forPremises(
            PremisesType premisesType,
            boolean landlordVatRegistered
    ) {
        if (premisesType == PremisesType.COMMERCIAL && landlordVatRegistered) {
            return STANDARD_RATED;
        }
        return VAT_EXEMPT;
    }
}
