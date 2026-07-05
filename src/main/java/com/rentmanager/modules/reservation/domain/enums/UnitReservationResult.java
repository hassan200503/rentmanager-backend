package com.rentmanager.modules.reservation.domain.enums;

import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;

/**
 * Carries the PaymentIntent created by reserveUnitAndCreateIntent() together
 * with the unit's display-friendly number and the landlord's resolved
 * Daraja credentials, both captured at lock time. Needed because
 * InitiateReservationRequest only carries unitId (a UUID, unsuitable for
 * the customer-facing M-Pesa STK push prompt), and neither the Unit nor its
 * owning Tenant are otherwise available to the caller once this
 * lock-holding transaction has committed and released.
 */
public record UnitReservationResult(
        PaymentIntent paymentIntent,
        String unitNumber,
        DarajaCredentials darajaCredentials
) {}