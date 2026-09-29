package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A user as returned by the API.
 *
 * <p>There is no password field of any kind - not the hash, not a masked value.
 */
@Schema(name = "User")
public record UserDto(
        Long id,
        String username,
        String fullName,
        String email,
        String mobileNumber,
        boolean active,
        boolean accountLocked,
        boolean mustChangePassword,
        Instant lastLoginAt,
        List<String> roles,
        @Schema(description = "Count of permissions granted directly to this user")
        int directPermissionCount,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
