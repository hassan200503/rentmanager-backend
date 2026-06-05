package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.model.PropertyCategory;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyCategoryJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PropertyCategoryPersistenceMapper {

    /**
     * JPA → Domain
     */
    PropertyCategory toDomain(PropertyCategoryJpaEntity entity);

    /**
     * Domain → JPA
     */
    PropertyCategoryJpaEntity toJpaEntity(PropertyCategory category);
}