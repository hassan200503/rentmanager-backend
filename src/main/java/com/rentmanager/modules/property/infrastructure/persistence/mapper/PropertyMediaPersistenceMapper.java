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
    default PropertyMedia toDomain(PropertyMediaJpaEntity entity) {
        if (entity == null) return null;
        PropertyMedia domain = PropertyMedia.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getPropertyId(),
                entity.getMediaType(),
                entity.getFileUrl(),
                entity.getFileName(),
                entity.getPublicId(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getPrimaryMedia(),
                entity.getUploadedAt(),
                entity.getCaption(),
                entity.getSortOrder(),
                entity.getVersion()
        );
        return domain;
    }

    /**
     * Domain → JPA
     */
    PropertyMediaJpaEntity toJpaEntity(PropertyMedia media);
}