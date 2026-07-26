package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.dto.ReservationDetailResponse;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationDetailQueryService {

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public ReservationDetailResponse getDetail(UUID reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found", ErrorCode.RESOURCE_NOT_FOUND
                ));

        return new ReservationDetailResponse(
                r.getId(),
                r.getFullName(),
                r.getPhone(),
                r.getEmail(),
                r.getNationalId(),
                r.getMpesaPhone(),
                r.getMoveInDate(),
                r.getDepositAmount(),
                r.getStatus(),
                r.getMpesaReceiptNumber(),
                r.getPaymentIntentId(),
                null,
                null
        );
    }
}
