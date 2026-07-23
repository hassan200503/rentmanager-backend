package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Safaricom posts here after a rent-payment STK push completes or is
 * cancelled. Deliberately a separate, public (/api/v1/public/**, permitAll)
 * controller from RentPaymentController — that one is authenticated
 * landlord-facing, this one is an unauthenticated webhook, and the two
 * security zones should never share a class the way ReservationController
 * mixes them for the deposit flow (kept split here on purpose).
 *
 * Secret-gating is identical to ReservationController#mpesaCallback:
 * daraja.callback-secret as a path segment, constant-time compared via
 * MessageDigest.isEqual, mismatch returns 404 (not 401/403) so the
 * endpoint's existence isn't confirmed to a prober. This is the SAME
 * secret as the deposit flow (see DarajaProperties.rentPaymentCallbackUrl
 * javadoc) — one platform trust boundary, not two — but a DIFFERENT path,
 * which is what actually keeps rent-payment callbacks from being delivered
 * to the deposit endpoint (see DarajaService's 6-arg initiateSTKPush
 * javadoc for the bug this fixes).
 *
 * POST /api/v1/public/rent-ledger/mpesa/callback/{secret}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/rent-ledger")
@RequiredArgsConstructor
public class RentPaymentCallbackController {

    private final RentPaymentCallbackService rentPaymentCallbackService;
    private final DarajaProperties darajaProperties;

    @PostMapping("/mpesa/callback/{secret}")
    public ResponseEntity<Void> mpesaCallback(
            @PathVariable String secret,
            @RequestBody MpesaCallbackPayload payload
    ) {
        String expected = darajaProperties.getCallbackSecret();

        if (expected == null || expected.isBlank()
                || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rent payment M-Pesa callback rejected: invalid or missing secret path segment");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        rentPaymentCallbackService.handle(payload);
        return ResponseEntity.ok().build();
    }
}
