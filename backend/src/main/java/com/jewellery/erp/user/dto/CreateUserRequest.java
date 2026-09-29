package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(name = "CreateUserRequest")
public record CreateUserRequest(
        @Schema(example = "staff1")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may contain only letters, digits, dot, underscore and hyphen")
        String username,

        @Schema(example = "Ramesh Kumar")
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @Email(message = "Enter a valid e-mail address")
        @Size(max = 150, message = "E-mail must not exceed 150 characters")
        String email,

        @Pattern(regexp = "^$|^[0-9+][0-9 -]{5,19}$", message = "Enter a valid mobile number")
        String mobileNumber,

        @Schema(example = "S3cure-Passw0rd",
                description = "Minimum 8 characters, with at least one letter and one digit")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password,

        @Schema(description = "Role ids. Defaults to ROLE_USER when omitted.")
        Set<Long> roleIds,

        @Schema(description = "Permission ids granted directly to this user")
        Set<Long> permissionIds,

        @Schema(description = "Force a password change at first sign-in", example = "true")
        Boolean mustChangePassword) {}
