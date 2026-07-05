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
@RequestMapping("/api/users")
public class UserController {

    private final UserCommandService userCommandService;

    public UserController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    // ------------------------------------------------------------
    // INVITE USER (STAFF/MANAGER)
    // ------------------------------------------------------------
    // Coarse gate: only OWNER or MANAGER may reach this endpoint at all —
    // STAFF is blocked here at the framework level. The finer-grained rule
    // (MANAGER may only invite STAFF, not MANAGER/OWNER) is data-dependent
    // and enforced inside UserCommandServiceImpl, since @PreAuthorize alone
    // can't inspect the request body against the caller's own role.
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<InviteUserResponse>> inviteUser(
            @Valid @RequestBody InviteUserRequest request
    ) {
        InviteUserResponse response = userCommandService.inviteUser(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}