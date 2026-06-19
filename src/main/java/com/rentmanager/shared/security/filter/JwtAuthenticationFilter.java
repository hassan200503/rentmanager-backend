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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.rentmanager.shared.security.context.TenantContext;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
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

            // =========================================================
            // JWT AUTH PATH (FIXED: now explicitly guards failure cases)
            // =========================================================
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