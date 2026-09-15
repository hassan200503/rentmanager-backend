package com.rentmanager.modules.rentledger.api.controller;

/** Test-only bridge to the package-private header validator. */
public final class RentLedgerCommandControllerKeyValidationProbe {

    private RentLedgerCommandControllerKeyValidationProbe() {
    }

    public static void validate(String key) {
        RentLedgerCommandController.requireValidIdempotencyKey(key);
    }
}
