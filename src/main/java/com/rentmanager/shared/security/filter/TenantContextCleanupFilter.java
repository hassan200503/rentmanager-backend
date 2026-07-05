package com.rentmanager.shared.security.filter;

import com.rentmanager.shared.security.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Addendum 3 §2 — TenantContext ThreadLocal cleanup.
 *
 * PROBLEM THIS FIXES:
 * TenantContext (shared.security.context.TenantContext) holds real, static
 * ThreadLocal<UUID> fields (CURRENT_TENANT, CURRENT_USER), populated during
 * request processing (by ClerkJwtAuthenticationConverter, per Addendum 2 §2).
 * Prior to this fix, TenantContext.clear() was never called anywhere in the
 * application. The now-retired JwtAuthenticationFilter's finally-block
 * clear() call looked like it handled this, but it only ever called
 * SecurityContextHolder.clearContext() and the vestigial
 * TenantContextHolder.clear() — never the real TenantContext.clear(). See
 * JwtAuthenticationFilter's retirement javadoc (§1.1) for the full history.
 *
 * On a pooled Tomcat thread executor, an un-cleared ThreadLocal can leak a
 * previous request's tenant/user ID into a later, unrelated request handled
 * by the same worker thread — a real tenant-isolation risk in a
 * multi-tenant SaaS app, independent of and in addition to the §1.5/§1.7
 * findings from this session.
 *
 * WHY THIS FILTER IS ORDERED AT Ordered.HIGHEST_PRECEDENCE:
 * Servlet filters execute in onion-layer order: pre-chain code runs
 * outermost-first, post-chain (finally) code runs outermost-last. This
 * filter must be the outermost layer so its cleanup in the finally block
 * runs strictly after every other filter — including Spring Security's own
 * FilterChainProxy (registered around order -100 by default) — has fully
 * unwound. Registering this at a lower precedence would risk clearing
 * TenantContext before some other filter or async callback finishes
 * reading it.
 *
 * This filter does not SET TenantContext — only clears it. Population is
 * still the responsibility of ClerkJwtAuthenticationConverter during
 * Spring Security's own authentication step, which runs inside the
 * filterChain.doFilter(...) call below.
 *
 * ORDERING CONFIRMED EMPIRICALLY (Addendum 5 §2): verified via temporary
 * log instrumentation against real authenticated requests (mvn
 * spring-boot:run + org.springframework.security DEBUG logging) that this
 * filter's ENTRY precedes FilterChainProxy's "Securing" line, and its EXIT
 * (TenantContext.clear()) follows FilterChainProxy's "Secured" line and the
 * BearerTokenAuthenticationFilter's SecurityContextHolder population — on
 * every authenticated request checked. No leak risk demonstrated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}