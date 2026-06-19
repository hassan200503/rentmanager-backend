package com.rentmanager.modules.user.infrastructure.persistence.mapper;

import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface UserPersistenceMapper {

    UserEntity toJpaEntity(User user);

    default User toDomain(UserEntity entity) {

        if (entity == null) {
            return null;
        }

        return User.rehydrate(
                entity.getId(),
                entity.getVersion(),
                entity.getClerkUserId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.isActive()
        );
    }
}