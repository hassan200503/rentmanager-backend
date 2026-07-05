package com.rentmanager.modules.user.application.command.service;

import com.rentmanager.modules.user.application.dto.request.InviteUserRequest;
import com.rentmanager.modules.user.application.dto.response.InviteUserResponse;

public interface UserCommandService {

    InviteUserResponse inviteUser(InviteUserRequest request);
}