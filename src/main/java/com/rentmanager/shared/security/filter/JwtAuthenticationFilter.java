package com.rentmanager.shared.security.filter;

import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.JwtClaims;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RETIRED — Addendum 3 §1.1, resolved this session.
 *
 * This filter is NO LONGER REGISTERED as a servlet filter. The @Component
 * annotation has been deliberately removed so Spring Boot does not
 * auto-detect and register it in the filter chain. The class is retained
 * (not deleted) only for reference and rollback safety ahead of pilot launch.
 * Do not re-add @Component without re-reading the findings below.
 *
 * BACKGROUND:
 * This filter ran on every request via Spring Boot's default component-scan
 * auto-registration, with no explicit @Order or FilterRegistrationBean,
 * placing it after Spring Security's own FilterChainProxy (which performs
 * the real, currently-relied-upon Clerk JWT verification via
 * SecurityConfig's oauth2ResourceServer/JWKS setup).
 *
 * This created a theoretical confused-deputy risk: IF this filter's
 * independent JwtProvider.isTokenValid(token) check ever returned true for
 * a genuine Clerk-issued token, it would overwrite SecurityContextHolder's
 * already-correct principal (set moments earlier by Clerk's own converter)
 * with a differently-constructed AuthenticatedUser built from this filter's
 * own claims extraction.
 *
 * EMPIRICAL FINDING (this session, real request logs, not static analysis):
 * JwtProvider signs/verifies using a separate, legacy HS256 scheme with its
 * own secret and issuer (see JwtProvider.java) — entirely unrelated to
 * Clerk's RS256/JWKS-based tokens. Every real Clerk-issued token tested
 * against jwtProvider.isTokenValid(token) returned false, across multiple
 * real authenticated requests (/api/v1/properties, /api/v1/units/summary).
 * The confused-deputy branch never executed even once.
 *
 * CONCLUSION: this filter provided zero function for real Clerk-based
 * authenticated traffic — it appears to be a leftover from a pre-Clerk,
 * self-issued JWT auth system (see JwtProvider.generateAccessToken /
 * generateRefreshToken) that was never removed when Clerk was adopted.
 * Retiring it removes dead/misleading code and closes the (theoretical,
 * never-triggered) confused-deputy risk permanently, with no behavior
 * change to real traffic.
 *
 * NOT YET DONE (see Addendum 3 §2, now unblocked by this decision):
 * TenantContext's real ThreadLocals are still never cleared on this app's
 * pooled thread executor. Since this filter's finally-block clear() call is
 * being retired along with it, a dedicated replacement cleanup mechanism
 * (a new, explicitly-ordered filter, or equivalent) is still required and
 * has NOT been implemented yet. Do not consider tenant-context cleanup
 * resolved until §2 is separately addressed.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final SecurityContextService securityContextService;

    public JwtAuthenticationFilter(

            JwtProvider jwtProvider,
            SecurityContextService securityContextService
    ) {
        this.jwtProvider = jwtProvider;
        this.securityContextService = securityContextService;
    }

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {

            String token = extractToken(request);

            boolean authenticated = false;

            if (StringUtils.hasText(token)
                    && jwtProvider.isTokenValid(token)
                    && jwtProvider.isAccessToken(token)) {

                JwtClaims claims = jwtProvider.extractClaims(token);

                AuthenticatedUser authenticatedUser =
                        buildAuthenticatedUser(claims);

                securityContextService.setAuthentication(authenticatedUser);

                authenticated = true;
            }

            filterChain.doFilter(request, response);

        } finally {
            securityContextService.clear();
        }
    }




    private String extractToken(HttpServletRequest request) {

        String authorizationHeader =
                request.getHeader(AUTHORIZATION);

        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private AuthenticatedUser buildAuthenticatedUser(
            JwtClaims claims
    ) {

        Set<SimpleGrantedAuthority> authorities =
                claims.roles()
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet());

        return new AuthenticatedUser(
                claims.userId(),
                claims.tenantId(),
                claims.email(),
                "",
                true,
                authorities
        );
    }


}