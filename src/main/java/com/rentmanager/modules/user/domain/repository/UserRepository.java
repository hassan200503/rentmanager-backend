package com.rentmanager.modules.user.domain.repository;

import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByClerkUserId(String clerkUserId);

    boolean existsByTenantId(UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Phase 2a: org-level managers of a landlord account. A manager is a
     * User with role MANAGER within the tenant org - there is no
     * property-level assignment, the manager serves the whole portfolio.
     */
    List<User> findByTenantIdAndRole(UUID tenantId, UserRole role);

    User save(User user);
}