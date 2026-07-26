package com.rentmanager.modules.reservation.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ResendSignInLinkRequest(
        UUID reservationId,
        @NotBlank String phone
) {}
