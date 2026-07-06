package com.rentmanager.modules.user.domain.repository;

import com.rentmanager.modules.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByClerkUserId(String clerkUserId);

    boolean existsByTenantId(UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    User save(User user);
}