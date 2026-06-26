package com.rentmanager.modules.property.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderPropertyMediaBatchRequest(
        @Valid
        @NotEmpty
        List<ReorderPropertyMediaRequest> items
) {
}