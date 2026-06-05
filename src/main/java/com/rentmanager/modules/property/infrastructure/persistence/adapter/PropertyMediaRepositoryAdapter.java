package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyMediaPersistenceMapper;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyMediaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyMediaRepositoryAdapter implements PropertyMediaRepository {

    private final PropertyMediaJpaRepository jpaRepository;
    private final PropertyMediaPersistenceMapper mapper;

    @Override
    public PropertyMedia save(PropertyMedia media) {
        var entity = mapper.toJpaEntity(media);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PropertyMedia> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<PropertyMedia> findAllByPropertyId(UUID propertyId) {
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