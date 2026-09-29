package com.jewellery.erp.auth.dto;

import com.jewellery.erp.security.UserPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * The signed-in user as the SPA needs it.
 *
 * <p>Carries the effective permission codes so the client can hide actions the
 * user cannot perform. This is a UX affordance only - every one of those actions
 * is independently enforced on the server.
 */
@Schema(name = "AuthenticatedUser")
public record AuthenticatedUserDto(
        Long id,
        String username,
        String fullName,
        Set<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {

    public static AuthenticatedUserDto from(UserPrincipal principal) {
        return new AuthenticatedUserDto(
                principal.id(),
                principal.username(),
                principal.fullName(),
                principal.roles(),
                principal.permissions().stream().sorted().toList(),
                principal.mustChangePassword());
    }
}
