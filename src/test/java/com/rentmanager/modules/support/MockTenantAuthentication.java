package com.rentmanager.modules.support;

import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;

public final class MockTenantAuthentication {

    private MockTenantAuthentication() {}

    public static RequestPostProcessor asTenant(UUID tenantId) {
        return asTenant(UUID.randomUUID(), tenantId);
    }

    public static RequestPostProcessor asTenant(UUID userId, UUID tenantId) {

        AuthenticatedUser user = new AuthenticatedUser(
                userId, tenantId, "test@test.com", "", true, Set.of()
        );
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext ctx = new SecurityContextImpl(auth);

        // Delegates to the official spring-security-test post-processor rather
        // than setting SecurityContextHolder directly. Setting it directly here
        // doesn't survive to the controller: SecurityContextHolderFilter runs
        // later in the filter chain and replaces whatever's on the holder with
        // a deferred supplier from the configured SecurityContextRepository,
        // silently discarding anything set before the chain executes. The
        // securityContext(...) post-processor integrates with the
        // TestSecurityContextHolderPostProcessor filter (auto-registered by
        // springSecurity(), which Boot wires in automatically when
        // spring-security-test is on the classpath) so the context is applied
        // at the correct point in the chain and actually reaches
        // @AuthenticationPrincipal.
        RequestPostProcessor securityContextProcessor = securityContext(ctx);

        return request -> {
            // TenantContext is set directly here because some controllers/
            // interceptors read it before or independently of the Authentication
            // resolution path — this mirrors what ClerkJwtAuthenticationConverter
            // would populate in production, which never runs in these tests.
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(userId);
            return securityContextProcessor.postProcessRequest(request);
        };
    }
}