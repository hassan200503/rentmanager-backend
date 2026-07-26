package com.rentmanager.modules.reservation.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationResponse;
import com.rentmanager.modules.reservation.application.dto.PaymentStatusResponse;
import com.rentmanager.modules.reservation.application.dto.ResendSignInLinkRequest;
import com.rentmanager.modules.reservation.application.dto.ResendSignInLinkResponse;
import com.rentmanager.modules.reservation.application.dto.ReservationDetailResponse;
import com.rentmanager.modules.reservation.application.service.InitiateReservationService;
import com.rentmanager.modules.reservation.application.service.PaymentStatusQueryService;
import com.rentmanager.modules.reservation.application.service.ResendSignInLinkService;
import com.rentmanager.modules.reservation.application.service.ReservationDetailQueryService;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/reservations")
public class ReservationController {

    private final InitiateReservationService initiateReservationService;
    private final MpesaCallbackService mpesaCallbackService;
    private final PaymentStatusQueryService paymentStatusQueryService;
    private final ReservationDetailQueryService reservationDetailQueryService;
    private final ResendSignInLinkService resendSignInLinkService;
    private final DarajaProperties darajaProperties;

    /**
     * Step 1 of the reservation flow.
     * Receives form data, creates a PaymentIntent, triggers M-Pesa STK Push.
     * Returns paymentIntentId for the frontend to poll payment status.
     *
     * POST /api/v1/public/reservations/initiate
     */
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<InitiateReservationResponse>> initiate(
            @Valid @RequestBody InitiateReservationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "STK Push sent. Awaiting payment.",
                initiateReservationService.initiate(request)
        ));
    }

    /**
     * Safaricom posts here after the customer completes or cancels payment.
     * Must return 200 quickly — Safaricom retries if it doesn't get a response.
     *
     * The {secret} path segment must match daraja.callback-secret. Daraja does
     * not sign callback payloads, so this is the only thing gating this endpoint
     * from the open internet — /api/v1/public/** is permitAll() in SecurityConfig.
     * Only Safaricom (via the callback-url registered with them) should know
     * the full path. Comparison uses MessageDigest.isEqual for constant-time
     * matching, and a mismatch returns 404 (not 401/403) so the endpoint's
     * existence isn't confirmed to a prober.
     *
     * POST /api/v1/public/reservations/mpesa/callback/{secret}
     */
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
            log.warn("M-Pesa callback rejected: invalid or missing secret path segment");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        mpesaCallbackService.handle(payload);
        return ResponseEntity.ok().build();
    }

    /**
     * Frontend polls this every 3 seconds on the waiting screen.
     * Returns PENDING | PAID | FAILED | EXPIRED.
     *
     * GET /api/v1/public/reservations/payment-status?id={paymentIntentId}
     */
    @GetMapping("/payment-status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> paymentStatus(
            @RequestParam UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Payment status retrieved",
                paymentStatusQueryService.getStatus(id)
        ));
    }

    /**
     * Confirmation page fetches this to show reservation details.
     * Returns tenant name, deposit amount, status, etc.
     *
     * GET /api/v1/public/reservations/{reservationId}
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponse>> getDetail(
            @PathVariable UUID reservationId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reservation details retrieved",
                reservationDetailQueryService.getDetail(reservationId)
        ));
    }

    /**
     * Re-sends the sign-in link SMS for a completed reservation.
     * The caller must provide the reservation's phone number to
     * verify they know both the reservation ID and the contact phone.
     * <p>
     * POST /api/v1/public/reservations/{reservationId}/resend-link
     */
    @PostMapping("/{reservationId}/resend-link")
    public ResponseEntity<ApiResponse<ResendSignInLinkResponse>> resendLink(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ResendSignInLinkRequest request
    ) {
        boolean sent = resendSignInLinkService.resend(reservationId, request.phone());
        if (sent) {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Sign-in link sent",
                    new ResendSignInLinkResponse(true, "Sign-in link sent via SMS")
            ));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(
                "Could not send sign-in link — verify reservation ID and phone number",
                "RESEND_FAILED"
        ));
    }
}