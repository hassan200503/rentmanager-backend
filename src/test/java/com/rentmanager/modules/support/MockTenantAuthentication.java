package com.rentmanager.modules.support;

import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

public final class MockTenantAuthentication {

    private MockTenantAuthentication() {}

    public static RequestPostProcessor asTenant(UUID tenantId) {
        return asTenant(UUID.randomUUID(), tenantId);
    }

    public static RequestPostProcessor asTenant(UUID userId, UUID tenantId) {
        return (MockHttpServletRequest request) -> {
            AuthenticatedUser user = new AuthenticatedUser(
                    userId, tenantId, "test@test.com", "", true, Set.of()
            );
            var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            var ctx = new SecurityContextImpl(auth);
            SecurityContextHolder.setContext(ctx);
            request.setAttribute(
                    "org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT",
                    ctx
            );

            // Set TenantContext directly — interceptor may run after service layer in some test modes
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(userId);

            return request;
        };
    }
}
