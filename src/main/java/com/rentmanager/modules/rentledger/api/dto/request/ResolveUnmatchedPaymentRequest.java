package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ResolveUnmatchedPaymentRequest(
        @NotBlank UUID transactionId,
        @NotBlank UUID unitId
) {}
