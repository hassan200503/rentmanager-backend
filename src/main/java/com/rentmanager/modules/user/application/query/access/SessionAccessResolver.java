package com.rentmanager.modules.user.application.query.access;

import com.rentmanager.modules.user.application.dto.response.SessionAccessResponse;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure mapping from granted authorities to {@link SessionAccessResponse}.
 * Kept free of Spring context so the mapping is unit-tested exhaustively.
 */
public final class SessionAccessResolver {

    private SessionAccessResolver() {
    }

    public static SessionAccessResponse resolve(
            UUID userId,
            UUID tenantId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        Set<String> names = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String landlordRole = null;
        if (names.contains("ROLE_LANDLORD_OWNER")) {
            landlordRole = "OWNER";
        } else if (names.contains("ROLE_LANDLORD_MANAGER")) {
            landlordRole = "MANAGER";
        } else if (names.contains("ROLE_LANDLORD_STAFF")) {
            landlordRole = "STAFF";
        }

        // A landlord binding without a fine-grained role would be a data
        // defect; report no organisation rather than guess a role. The id is
        // only reported alongside a role the backend will honour.
        UUID landlordTenantId = landlordRole != null ? tenantId : null;

        return new SessionAccessResponse(
                userId,
                landlordRole,
                landlordTenantId,
                names.contains("ROLE_TENANT"),
                names.contains("ROLE_PENDING_ONBOARDING"),
                names.contains("ROLE_PLATFORM_ADMIN")
        );
    }
}
