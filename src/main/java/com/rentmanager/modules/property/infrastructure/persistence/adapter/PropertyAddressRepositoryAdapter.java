package com.rentmanager.modules.property.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.model.PropertyAddress;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.mapper.PropertyAddressPersistenceMapper;
import org.springframework.stereotype.Component;

/**
 * Adapter responsibility reduced to PURE mapping support.
 *
 * ARCHITECTURE RULE:
 * PropertyAddress is a VALUE OBJECT (Embeddable)
 * → It is persisted ONLY through PropertyJpaEntity
 * → It does NOT have its own repository
 */
@Component
public class PropertyAddressRepositoryAdapter {

    private final PropertyAddressPersistenceMapper mapper;

    public PropertyAddressRepositoryAdapter(PropertyAddressPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Convert domain → JPA embeddable
     */
    public PropertyAddressJpaEntity toJpa(PropertyAddress address) {
        if (address == null) {
            return null;
        }
        return mapper.toJpaEntity(address);
    }

    /**
     * Convert JPA embeddable → domain
     */
    public PropertyAddress toDomain(PropertyAddressJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return mapper.toDomain(entity);
    }
}