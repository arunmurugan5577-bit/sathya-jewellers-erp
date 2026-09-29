package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Administrator-initiated password reset. */
@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String newPassword,

        @Schema(description = "Require the user to choose a new password at next sign-in", example = "true")
        Boolean mustChangePassword) {}
