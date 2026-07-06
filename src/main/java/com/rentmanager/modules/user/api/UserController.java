package com.rentmanager.modules.user.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.user.application.command.service.UserCommandService;
import com.rentmanager.modules.user.application.dto.request.InviteUserRequest;
import com.rentmanager.modules.user.application.dto.response.InviteUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

@RequestMapping("/api/v1/users")
public class UserController {

    private final UserCommandService userCommandService;

    public UserController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<InviteUserResponse>> inviteUser(
            @Valid @RequestBody InviteUserRequest request
    ) {
        InviteUserResponse response = userCommandService.inviteUser(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}