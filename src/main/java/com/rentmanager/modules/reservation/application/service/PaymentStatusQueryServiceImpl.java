package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.dto.PaymentStatusResponse;
import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentStatusQueryServiceImpl implements PaymentStatusQueryService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getStatus(UUID paymentIntentId) {

        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PaymentIntent not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        UUID reservationId = null;
        if (intent.getStatus() == PaymentIntentStatus.PAID) {
            reservationId = reservationRepository
                    .findByPaymentIntentId(intent.getId())
                    .map(r -> r.getId())
                    .orElse(null);
        }

        return new PaymentStatusResponse(
                intent.getId(),
                intent.getStatus(),
                reservationId
        );
    }
}