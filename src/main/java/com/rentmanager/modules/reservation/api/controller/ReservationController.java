package com.rentmanager.modules.reservation.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationResponse;
import com.rentmanager.modules.reservation.application.dto.PaymentStatusResponse;
import com.rentmanager.modules.reservation.application.service.InitiateReservationService;
import com.rentmanager.modules.reservation.application.service.PaymentStatusQueryService;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/reservations")
public class ReservationController {

    private final InitiateReservationService initiateReservationService;
    private final MpesaCallbackService mpesaCallbackService;
    private final PaymentStatusQueryService paymentStatusQueryService;

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
     * POST /api/v1/public/reservations/mpesa/callback
     */
    @PostMapping("/mpesa/callback")
    public ResponseEntity<Void> mpesaCallback(
            @RequestBody MpesaCallbackPayload payload
    ) {
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
}