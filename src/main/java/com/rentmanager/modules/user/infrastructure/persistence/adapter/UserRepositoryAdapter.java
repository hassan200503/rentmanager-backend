package com.rentmanager.modules.user.infrastructure.persistence.adapter;

import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity;
import com.rentmanager.modules.user.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.rentmanager.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryAdapter(
            UserJpaRepository jpaRepository,
            UserPersistenceMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByClerkUserId(String clerkUserId) {
        return jpaRepository.findByClerkUserId(clerkUserId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTenantId(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return jpaRepository.existsByTenantId(tenantId);
    }

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toJpaEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}