package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationResponse;

public interface InitiateReservationService {

    InitiateReservationResponse initiate(InitiateReservationRequest request);
}