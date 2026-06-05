package com.rentmanager.modules.unit.domain.enums;

public enum PricingStrategy {

    FIXED,              // constant rent per month
    PER_UNIT,           // one price for entire unit (same as FIXED but explicit)
    PER_OCCUPANT,      // rent depends on number of occupants (future lease logic)
    NEGOTIATED         // manually agreed pricing (override all rules)
}