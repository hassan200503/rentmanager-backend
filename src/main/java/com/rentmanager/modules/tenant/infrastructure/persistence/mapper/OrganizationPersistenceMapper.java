package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.OrganizationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OrganizationPersistenceMapper {

    OrganizationEntity toJpaEntity(OrganizationEntity entity);

    OrganizationEntity toDomain(OrganizationEntity entity);
}