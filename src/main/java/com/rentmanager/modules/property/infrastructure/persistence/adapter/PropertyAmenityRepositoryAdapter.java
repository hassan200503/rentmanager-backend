package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.model.PropertyAmenity;
import com.rentmanager.modules.property.domain.repository.PropertyAmenityRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAmenityJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyAmenityPersistenceMapper;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyAmenityJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyAmenityRepositoryAdapter implements PropertyAmenityRepository {

    private final PropertyAmenityJpaRepository jpaRepository;
    private final PropertyAmenityPersistenceMapper mapper;

    @Override
    public PropertyAmenity save(PropertyAmenity amenity) {
        PropertyAmenityJpaEntity entity = mapper.toJpaEntity(amenity);
        PropertyAmenityJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PropertyAmenity> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<PropertyAmenity> findAllByPropertyId(UUID propertyId) {
        return jpaRepository.findAllByPropertyId(propertyId)
                .stream()
                .map(mapper::toDomain)
                .toList();
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