package com.rentmanager.modules.reservation.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

public record InitiateReservationRequest(

        @NotNull
        UUID unitId,

        @NotBlank
        String fullName,

        @NotBlank
        @Pattern(regexp = "^\\+2547\\d{8}$", message = "Enter a valid Kenyan number e.g. +254712345678")
        String phone,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String nationalId,

        @NotNull
        LocalDate moveInDate,

        @NotBlank
        @Pattern(regexp = "^\\+2547\\d{8}$", message = "Enter a valid M-Pesa number e.g. +254712345678")
        String mpesaPhone
) {}