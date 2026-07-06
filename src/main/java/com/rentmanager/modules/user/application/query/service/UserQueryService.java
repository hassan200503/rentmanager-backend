package com.rentmanager.modules.user.application.query.service;

import com.rentmanager.modules.user.application.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserQueryService {

    UserResponse getCurrentUser(UUID userId);

    Page<UserResponse> getByTenant(UUID tenantId, Pageable pageable);
}