package com.rentmanager.modules.tax.infrastructure.persistence.adapter;

import com.rentmanager.modules.tax.domain.model.PropertyTaxRegistration;
import com.rentmanager.modules.tax.domain.repository.PropertyTaxRegistrationRepository;
import com.rentmanager.modules.tax.infrastructure.persistence.mapper.PropertyTaxRegistrationPersistenceMapper;
import com.rentmanager.modules.tax.infrastructure.persistence.repository.PropertyTaxRegistrationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyTaxRegistrationRepositoryAdapter implements PropertyTaxRegistrationRepository {

    private final PropertyTaxRegistrationJpaRepository jpaRepository;
    private final PropertyTaxRegistrationPersistenceMapper mapper;

    @Override
    public Optional<PropertyTaxRegistration> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PropertyTaxRegistration> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId) {
        return jpaRepository.findByTenantIdAndPropertyId(tenantId, propertyId).map(mapper::toDomain);
    }

    @Override
    public List<PropertyTaxRegistration> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public PropertyTaxRegistration save(PropertyTaxRegistration registration) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(registration)));
    }
}