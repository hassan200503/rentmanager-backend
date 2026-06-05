package com.rentmanager.shared.security.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String password;
    private final boolean active;
    private final Set<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(
            UUID userId,
            UUID tenantId,
            String email,
            String password,
            boolean active,
            Set<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
        this.active = active;
        this.authorities = authorities;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }



    public String getEmail() {
        return email;
    }
}