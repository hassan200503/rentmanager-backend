package com.rentmanager.shared.security.context;

import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SecurityContextService {

    public Optional<AuthenticatedUser> getAuthenticatedUser() {

        Object principal = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }

        return Optional.empty();
    }

    public UUID requireCurrentUserId() {
        return getAuthenticatedUser()
                .map(AuthenticatedUser::getUserId)
                .orElseThrow(() ->
                        new IllegalStateException("No authenticated user found")
                );
    }

    public UUID requireCurrentTenantId() {
        return getAuthenticatedUser()
                .map(AuthenticatedUser::getTenantId)
                .orElseThrow(() ->
                        new IllegalStateException("No tenant context found")
                );
    }

    public void setAuthentication(AuthenticatedUser user) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        TenantContextHolder.setContext(
                new TenantContext()
        );
    }

    public void clear() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }
}