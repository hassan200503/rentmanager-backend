package com.rentmanager.modules.reservation.application.dto;

public record ResendSignInLinkResponse(
        boolean sent,
        String message
) {}
