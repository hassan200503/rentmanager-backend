package com.rentmanager.shared.security.jwt;

import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class ClerkAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser authenticatedUser;
    private final Jwt jwt;

    public ClerkAuthenticationToken(
            AuthenticatedUser authenticatedUser,
            Jwt jwt,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.authenticatedUser = authenticatedUser;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public Object getPrincipal() {
        return authenticatedUser;
    }
}