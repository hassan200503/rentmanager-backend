package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationResponse;
import com.rentmanager.modules.reservation.domain.enums.UnitReservationResult;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinates reservation initiation across a fast, lock-protected DB
 * transaction and a slow external Daraja STK push call.
 *
 * This class itself is NOT @Transactional. The actual DB work happens in
 * UnitReservationTransactionService, invoked as calls on a separate proxied
 * bean — never as internal method calls on `this` — so the PESSIMISTIC_WRITE
 * unit lock is held only for the short DB steps and is fully released
 * before the (potentially slow, potentially failing) call to Daraja.
 *
 * Failure modes:
 *  - Unit already taken (not VACANT), or landlord hasn't configured Daraja
 *    credentials yet -> reserveUnitAndCreateIntent throws before any
 *    external call is made. Propagates to the caller as-is.
 *  - STK push call itself fails (network/4xx/5xx from Daraja) -> caught
 *    here, unit released back to VACANT immediately via
 *    releaseUnitAndFailIntent, exception rethrown to the caller.
 *  - STK push succeeds but the async callback later reports failure ->
 *    handled separately, in MpesaCallbackService, not here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiateReservationServiceImpl implements InitiateReservationService {

    private final UnitReservationTransactionService transactionService;
    private final DarajaService darajaService;

    @Override
    public InitiateReservationResponse initiate(InitiateReservationRequest request) {

        // Step 1 (TX): lock unit row, validate VACANT, resolve landlord's
        // Daraja credentials, flip unit to PENDING_PAYMENT, create
        // PaymentIntent. Lock is released the instant this call returns.
        UnitReservationResult result = transactionService.reserveUnitAndCreateIntent(request);
        PaymentIntent intent = result.paymentIntent();

        // Step 2 (NO TX, NO LOCK HELD): slow external call, authenticated
        // against this specific landlord's own Daraja credentials.
        String checkoutRequestId;
        try {
            checkoutRequestId = darajaService.initiateSTKPush(
                    request.mpesaPhone(),
                    intent.getDepositAmount(),
                    result.unitNumber(),
                    "Deposit for Unit " + result.unitNumber(),
                    result.darajaCredentials()
            );
        } catch (Exception e) {
            transactionService.releaseUnitAndFailIntent(intent.getId(), e);
            throw e;
        }

        // Step 3 (TX): attach the checkoutRequestId now that we have it.
        transactionService.attachCheckoutRequestId(intent.getId(), checkoutRequestId);

        log.info("Reservation initiated. paymentIntentId={} checkoutRequestId={}",
                intent.getId(), checkoutRequestId);

        return new InitiateReservationResponse(intent.getId());
    }
}