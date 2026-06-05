package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantSubscriptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TenantSubscriptionPersistenceMapper {

    TenantSubscriptionEntity toJpaEntity(TenantSubscriptionEntity entity);

    TenantSubscriptionEntity toDomain(TenantSubscriptionEntity entity);
}