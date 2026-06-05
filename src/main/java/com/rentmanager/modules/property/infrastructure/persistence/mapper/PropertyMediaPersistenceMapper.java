package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyMediaJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PropertyMediaPersistenceMapper {

    /**
     * JPA → Domain
     */
    PropertyMedia toDomain(PropertyMediaJpaEntity entity);

    /**
     * Domain → JPA
     */
    PropertyMediaJpaEntity toJpaEntity(PropertyMedia media);
}