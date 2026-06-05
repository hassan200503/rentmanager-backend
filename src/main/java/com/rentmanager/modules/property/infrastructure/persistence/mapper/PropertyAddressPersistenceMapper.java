package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.model.PropertyAddress;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PropertyAddressPersistenceMapper {

    PropertyAddress toDomain(PropertyAddressJpaEntity entity);

    PropertyAddressJpaEntity toJpaEntity(PropertyAddress address);
}