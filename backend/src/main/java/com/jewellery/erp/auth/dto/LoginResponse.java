package com.jewellery.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Issued credentials.
 *
 * @param accessToken short lived JWT, sent as {@code Authorization: Bearer ...}
 * @param expiresIn seconds until {@code accessToken} expires
 * @param refreshToken opaque, revocable; exchanged at {@code /api/auth/refresh}
 */
@Schema(name = "LoginResponse")
public record LoginResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        long expiresIn,
        Instant expiresAt,
        String refreshToken,
        AuthenticatedUserDto user) {

    public static LoginResponse of(
            String accessToken,
            long expiresIn,
            Instant expiresAt,
            String refreshToken,
            AuthenticatedUserDto user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, expiresAt, refreshToken, user);
    }
}
