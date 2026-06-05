package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantSettingsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TenantSettingsPersistenceMapper {

    TenantSettingsEntity toJpaEntity(TenantSettingsEntity entity);

    TenantSettingsEntity toDomain(TenantSettingsEntity entity);
}