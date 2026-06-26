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
    public Optional<PropertyMedia> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository
                .findByIdAndTenantId(id, tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PropertyMedia> findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
            UUID tenantId,
            UUID propertyId
    ) {
        return jpaRepository
                .findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
                        tenantId,
                        propertyId
                )
                .map(mapper::toDomain);
    }

    @Override
    public List<PropertyMedia> findAllByTenantIdAndPropertyId(
            UUID tenantId,
            UUID propertyId
    ) {
        return jpaRepository
                .findAllByTenantIdAndPropertyId(
                        tenantId,
                        propertyId
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void delete(PropertyMedia media) {
        jpaRepository.delete(
                mapper.toJpaEntity(media)
        );
    }



    @Override
    public List<PropertyMedia> findAllByPropertyIdIn(List<UUID> propertyIds) {
        return jpaRepository.findAllByPropertyIdIn(propertyIds)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PropertyMedia> findAllByPropertyId(UUID propertyId) {
        return jpaRepository.findAllByPropertyId(propertyId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}