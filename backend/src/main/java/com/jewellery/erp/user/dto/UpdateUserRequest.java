package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Editable user attributes.
 *
 * <p>The username is intentionally absent: it is the identity the audit columns
 * record, so it is fixed once the account exists. Password changes go through
 * the dedicated reset endpoint, which also revokes live sessions.
 */
@Schema(name = "UpdateUserRequest")
public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Email(message = "Enter a valid e-mail address")
        @Size(max = 150, message = "E-mail must not exceed 150 characters")
        String email,

        @Pattern(regexp = "^$|^[0-9+][0-9 -]{5,19}$", message = "Enter a valid mobile number")
        String mobileNumber,

        @Schema(description = "Replaces the current role assignment")
        Set<Long> roleIds) {}
