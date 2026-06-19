package com.rentmanager.modules.user.domain.repository;

import com.rentmanager.modules.user.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByClerkUserId(String clerkUserId);

    User save(User user);
}