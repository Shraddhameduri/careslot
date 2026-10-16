package com.careslot.security;

import com.careslot.domain.Role;

/** Authenticated principal: user id + tenant id + role, from the JWT claims. */
public record TenantPrincipal(Long userId, Long tenantId, String email, Role role) {

    public org.springframework.security.core.Authentication authentication() {
        var authority = new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name());
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                this, null, java.util.List.of(authority));
    }
}
