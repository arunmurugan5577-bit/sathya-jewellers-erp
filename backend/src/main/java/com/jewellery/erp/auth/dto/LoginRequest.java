package com.jewellery.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @Schema(example = "admin")
        @NotBlank(message = "Username is required")
        @Size(max = 50, message = "Username must not exceed 50 characters")
        String username,

        @Schema(example = "S3cure-Passw0rd")
        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must not exceed 100 characters")
        String password) {}
