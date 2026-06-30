package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaCallbackService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void handle(MpesaCallbackPayload payload) {

        StkCallback callback = payload.getBody().getStkCallback();
        String checkoutRequestId = callback.getCheckoutRequestId();

        log.info("M-Pesa callback received. CheckoutRequestID={} ResultCode={}",
                checkoutRequestId, callback.getResultCode());

        // 1. Find PaymentIntent by CheckoutRequestID
        PaymentIntent intent = paymentIntentRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> {
                    log.error("No PaymentIntent found for CheckoutRequestID={}", checkoutRequestId);
                    return new IllegalStateException(
                            "No PaymentIntent found for CheckoutRequestID: " + checkoutRequestId
                    );
                });

        // 2. Handle failed payment
        if (!callback.isSuccessful()) {
            log.warn("M-Pesa payment failed. CheckoutRequestID={} Reason={}",
                    checkoutRequestId, callback.getResultDesc());
            intent.markFailed();
            paymentIntentRepository.save(intent);
            return;
        }

        // 3. Extract receipt number
        String mpesaReceiptNumber = callback.getCallbackMetadata().getMpesaReceiptNumber();
        if (mpesaReceiptNumber == null) {
            log.error("No MpesaReceiptNumber in callback for CheckoutRequestID={}", checkoutRequestId);
            intent.markFailed();
            paymentIntentRepository.save(intent);
            return;
        }

        // 4. Mark PaymentIntent paid
        intent.markPaid(mpesaReceiptNumber);
        paymentIntentRepository.save(intent);

        // 5. Deserialize form data stored in PaymentIntent
        InitiateReservationRequest formData;
        try {
            formData = objectMapper.readValue(
                    intent.getFormDataJson(),
                    InitiateReservationRequest.class
            );
        } catch (Exception e) {
            log.error("Failed to deserialize form data for PaymentIntent={}",
                    intent.getId(), e);
            throw new IllegalStateException("Failed to deserialize reservation form data", e);
        }

        // 6. Create and persist Reservation
        // propertyId comes from intent.getPropertyId() (stored on PaymentIntent at creation time)
        Reservation reservation = Reservation.create(
                intent.getUnitId(),
                intent.getPropertyId(),
                formData.fullName(),
                formData.phone(),
                formData.email(),
                formData.nationalId(),
                formData.mpesaPhone(),
                formData.moveInDate(),
                intent.getDepositAmount(),
                intent.getId()
        );

        // 7. Mark deposit paid — fires ReservationDepositPaidEvent
        reservation.markDepositPaid(mpesaReceiptNumber);
        reservationRepository.save(reservation);

        // 8. Publish domain events via the DomainEventPublisher abstraction
        eventPublisher.publishAll(reservation.pullDomainEvents());

        log.info("Reservation created and deposit paid event fired. reservationId={} receipt={}",
                reservation.getId(), mpesaReceiptNumber);
    }
}