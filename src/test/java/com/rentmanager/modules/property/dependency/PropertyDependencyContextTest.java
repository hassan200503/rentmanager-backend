package com.rentmanager.modules.property.dependency;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = RentManagerApplication.class)
class PropertyDependencyContextTest {

    @Autowired
    private PropertyCommandService service;

    @Test
    void shouldLoadPropertyCommandServiceBean() {
        assertNotNull(service);
    }
}