package com.jewellery.erp.security;

import com.jewellery.erp.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>Authorities hold both the role names ({@code ROLE_ADMIN}) and the effective
 * permission codes ({@code CATEGORY_CREATE}), which is what lets endpoints be
 * guarded with {@code hasAuthority('CATEGORY_CREATE')} rather than with role
 * checks that would need editing every time staff access changes.
 */
public record UserPrincipal(
        Long id,
        String username,
        String fullName,
        String password,
        boolean active,
        boolean accountLocked,
        boolean mustChangePassword,
        Set<String> roles,
        Set<String> permissions,
        Collection<? extends GrantedAuthority> authorities)
        implements UserDetails {

    /** Builds a principal from a fully loaded {@link User} entity (login path). */
    public static UserPrincipal from(User user) {
        Set<String> roleNames = user.roleNames();
        Set<String> permissionCodes = user.effectivePermissionCodes();
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPasswordHash(),
                user.isActive(),
                user.isAccountLocked(),
                user.isMustChangePassword(),
                roleNames,
                permissionCodes,
                toAuthorities(roleNames, permissionCodes));
    }

    /** Builds a principal from verified JWT claims (per-request path, no DB hit). */
    public static UserPrincipal fromClaims(
            Long id, String username, String fullName, Set<String> roles, Set<String> permissions) {
        return new UserPrincipal(
                id, username, fullName, null, true, false, false,
                roles, permissions, toAuthorities(roles, permissions));
    }

    private static List<GrantedAuthority> toAuthorities(Set<String> roles, Set<String> permissions) {
        return Stream.concat(roles.stream(), permissions.stream())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
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
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
