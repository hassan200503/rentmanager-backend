package com.rentmanager.modules.tax.domain.enums;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VAT treatment of rental invoices by premises classification.
 * MIXED_USE must fail-closed to VAT_EXEMPT: without unit-level income
 * apportionment the commercial portion cannot be isolated, so 16% VAT is
 * never guessed for an unsplit mixed building.
 */
class VatTreatmentTest {

    @Test
    void residentialIsAlwaysVatExemptEvenWhenRegistered() {
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(PremisesType.RESIDENTIAL, true));
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(PremisesType.RESIDENTIAL, false));
    }

    @Test
    void commercialIsStandardRatedOnlyWhenVatRegistered() {
        assertEquals(VatTreatment.STANDARD_RATED,
                VatTreatment.forPremises(PremisesType.COMMERCIAL, true));
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(PremisesType.COMMERCIAL, false));
    }

    @Test
    void mixedUseIsFailClosedToVatExempt() {
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(PremisesType.MIXED_USE, true));
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(PremisesType.MIXED_USE, false));
    }

    @Test
    void nullPremisesIsVatExempt() {
        assertEquals(VatTreatment.VAT_EXEMPT,
                VatTreatment.forPremises(null, true));
    }
}