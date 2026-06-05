package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AssignTenantOwnerRequest {

    private String name;          // ✅ FIXED
    private String email;
    private String phoneNumber;
    private String identifier;    // ✅ FIXED
}