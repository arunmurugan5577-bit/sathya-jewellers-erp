package com.jewellery.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest")
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {}
