package com.rentmanager.modules.reservation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationResponse;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitiateReservationServiceImpl implements InitiateReservationService {

    private final UnitRepository unitRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final DarajaService darajaService;
    private final ObjectMapper objectMapper;

    private static final int DEPOSIT_MONTHS = 2;

    @Override
    @Transactional
    public InitiateReservationResponse initiate(InitiateReservationRequest request) {

        // 1. Load unit — validate it exists and is vacant
        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit not found", ErrorCode.UNIT_NOT_FOUND
                ));

        // 2. Calculate deposit
        BigDecimal depositAmount = unit.getRentAmount()
                .multiply(BigDecimal.valueOf(DEPOSIT_MONTHS));

        // 3. Serialize form data to JSON for storage in PaymentIntent
        String formDataJson;
        try {
            formDataJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reservation form data", e);
        }

        // 4. Create and persist PaymentIntent
        PaymentIntent intent = PaymentIntent.create(
                request.unitId(),
                unit.getPropertyId(),
                formDataJson,
                depositAmount

        );
        intent = paymentIntentRepository.save(intent);

        // 5. Trigger STK Push
        String checkoutRequestId = darajaService.initiateSTKPush(
                request.mpesaPhone(),
                depositAmount,
                unit.getUnitNumber(),           // shown on customer's M-Pesa screen
                "Deposit for Unit " + unit.getUnitNumber()
        );

        // 6. Attach checkoutRequestId and persist again
        intent.attachCheckoutRequestId(checkoutRequestId);
        paymentIntentRepository.save(intent);

        log.info("Reservation initiated. paymentIntentId={} checkoutRequestId={}",
                intent.getId(), checkoutRequestId);

        return new InitiateReservationResponse(intent.getId());
    }
}