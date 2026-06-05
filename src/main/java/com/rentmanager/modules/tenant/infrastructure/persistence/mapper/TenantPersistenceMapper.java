package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TenantPersistenceMapper {

    // ============================
    // DOMAIN → JPA ENTITY
    // ============================
    TenantEntity toJpaEntity(Tenant tenant);

    // ============================
    // JPA ENTITY → DOMAIN
    // ============================
    @ObjectFactory
    default Tenant toDomain(TenantEntity entity) {

        if (entity == null) return null;

        return Tenant.create(
                entity.getTenantCode(),
                entity.getName(),
                entity.getSlug(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getType()
        );
    }
}