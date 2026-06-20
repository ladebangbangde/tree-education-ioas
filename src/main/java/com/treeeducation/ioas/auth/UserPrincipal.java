package com.treeeducation.ioas.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Authenticated user principal exposed to controllers. */
public record UserPrincipal(Long id, String username, String userName, String role, String department) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (normalizedRole.startsWith("ROLE_")) {
            normalizedRole = normalizedRole.substring("ROLE_".length());
        }

        Set<String> roles = new LinkedHashSet<>();
        if (!normalizedRole.isBlank()) {
            roles.add("ROLE_" + normalizedRole);
        }

        // Data-operation report export is an internal OA feature. Some existing accounts use legacy
        // role codes such as ADMIN / OPERATOR instead of DATA, so grant DATA authority to any
        // authenticated OA account to keep the existing download flow working.
        roles.add("ROLE_DATA");

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String roleName : roles) {
            authorities.add(new SimpleGrantedAuthority(roleName));
        }
        return authorities;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
