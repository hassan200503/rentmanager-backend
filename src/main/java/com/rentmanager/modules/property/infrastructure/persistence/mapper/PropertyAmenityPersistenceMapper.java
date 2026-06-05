package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.model.PropertyAmenity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAmenityJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PropertyAmenityPersistenceMapper {

    /**
     * JPA → Domain
     */
    PropertyAmenity toDomain(PropertyAmenityJpaEntity entity);

    /**
     * Domain → JPA
     */
    PropertyAmenityJpaEntity toJpaEntity(PropertyAmenity amenity);
}