package com.rentmanager.modules.user.application.query.service;

import com.rentmanager.modules.user.application.dto.response.UserResponse;
import com.rentmanager.modules.user.application.mapper.UserResponseMapper;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be resolved"));

        return UserResponseMapper.toResponse(user);
    }

    @Override
    public Page<UserResponse> getByTenant(UUID tenantId, Pageable pageable) {
        // ASSUMPTION: UserRepository needs this method added —
        // it does not exist in the file shared so far.
        Page<User> users = userRepository.findByTenantId(tenantId, pageable);
        return users.map(UserResponseMapper::toResponse);
    }
}