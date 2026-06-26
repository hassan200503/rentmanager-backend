package com.rentmanager.modules.property.application.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePropertyMediaRequest(

        @Size(max = 500)
        String caption

) {
}