package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeTenantOwnerRequest {

    private String name;          // ✅ FIXED
    private String email;         // ✅ FIXED
    private String phoneNumber;   // ✅ FIXED
    private String identifier;    // ✅ FIXED
}