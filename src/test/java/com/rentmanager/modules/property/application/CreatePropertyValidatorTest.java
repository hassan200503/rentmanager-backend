package com.rentmanager.modules.property.application;

import com.rentmanager.modules.property.application.command.validator.CreatePropertyValidator;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validator governance for the premises classification override.
 * Manual mocks only (no Mockito extension) per project conventions.
 */
class CreatePropertyValidatorTest {

    private final UUID TENANT_ID = UUID.randomUUID();

    private PropertyRepository repository;
    private CreatePropertyValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(PropertyRepository.class);
        when(repository.existsByTenantIdAndNameIgnoreCase(TENANT_ID, "Green Villa"))
                .thenReturn(false);
        validator = new CreatePropertyValidator(repository);
    }

    private CreatePropertyRequest request(PremisesType premises, String reason) {
        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Green Villa");
        request.setPropertyType(PropertyType.APARTMENT);
        request.setPremisesType(premises);
        request.setPremisesTypeOverrideReason(reason);
        return request;
    }

    @Test
    void autoClassifiedRequestWithoutReasonIsAccepted() {
        assertDoesNotThrow(() -> validator.validate(TENANT_ID, request(null, null)));
    }

    @Test
    void overrideWithoutReasonIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(TENANT_ID, request(PremisesType.MIXED_USE, null)));

        assertEquals(
                "premisesTypeOverrideReason is required when premisesType is provided",
                error.getMessage()
        );
    }

    @Test
    void blankReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(TENANT_ID, request(PremisesType.COMMERCIAL, "   ")));
    }

    @Test
    void overLengthReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(TENANT_ID, request(PremisesType.RESIDENTIAL, "x".repeat(501))));
    }

    @Test
    void reasonWithoutPremisesTypeIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(TENANT_ID, request(null, "Because")));

        assertEquals(
                "premisesTypeOverrideReason cannot be provided without an explicit premisesType",
                error.getMessage()
        );
    }

    @Test
    void validOverrideWithReasonIsAccepted() {
        assertDoesNotThrow(() ->
                validator.validate(TENANT_ID, request(PremisesType.MIXED_USE, "Shops below, flats above")));
    }
}
