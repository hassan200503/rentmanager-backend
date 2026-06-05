package com.rentmanager.modules.property.dependency;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class PropertyDependencyWiringTest {

    @InjectMocks
    private PropertyCommandServiceImpl service;

    @Mock private com.rentmanager.modules.property.domain.repository.PropertyRepository repository;
    @Mock private com.rentmanager.modules.property.application.mapper.PropertyMapper mapper;

    @Mock private com.rentmanager.modules.property.application.command.validator.CreatePropertyValidator createPropertyValidator;
    @Mock private com.rentmanager.modules.property.application.command.validator.UpdatePropertyValidator updatePropertyValidator;
    @Mock private com.rentmanager.modules.property.application.command.validator.ActivatePropertyValidator activatePropertyValidator;
    @Mock private com.rentmanager.modules.property.application.command.validator.MarkFullyOccupiedValidator markFullyOccupiedValidator;
    @Mock private com.rentmanager.modules.property.application.command.validator.PropertyMarkVacantValidator propertyMarkVacantValidator;

    @Test
    void shouldWireAllDependenciesCorrectly() {

        assertNotNull(service);
    }




}