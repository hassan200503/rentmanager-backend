package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.lease.application.port.out.PropertyQueryPort;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PropertyQueryAdapter implements PropertyQueryPort {

    private final PropertyJpaRepository propertyJpaRepository;

    public PropertyQueryAdapter(PropertyJpaRepository propertyJpaRepository) {
        this.propertyJpaRepository = propertyJpaRepository;
    }

    @Override
    public boolean existsById(UUID propertyId) {
        return propertyJpaRepository.existsById(propertyId);
    }
}