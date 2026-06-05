package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.model.PropertyCategory;
import com.rentmanager.modules.property.domain.repository.PropertyCategoryRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyCategoryJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyCategoryPersistenceMapper;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyCategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyCategoryRepositoryAdapter implements PropertyCategoryRepository {

    private final PropertyCategoryJpaRepository jpaRepository;
    private final PropertyCategoryPersistenceMapper mapper;

    @Override
    public PropertyCategory save(PropertyCategory category) {
        PropertyCategoryJpaEntity entity = mapper.toJpaEntity(category);
        PropertyCategoryJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PropertyCategory> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PropertyCategory> findByNameAndTenantId(String name, UUID tenantId) {
        return jpaRepository.findByNameAndTenantId(name, tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}