package com.jewellery.erp.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration, bound from {@code app.jwt.*}.
 *
 * @param secret Base64 encoded signing key, at least 256 bits. Supplied through
 *     the {@code JWT_SECRET} environment variable - the application refuses to
 *     start without it, which is preferable to silently running on a default.
 * @param issuer value placed in, and required from, the {@code iss} claim
 * @param accessTokenValidity short lived; the window in which a revoked user can
 *     still call the API
 * @param refreshTokenValidity lifetime of the persisted, revocable refresh token
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET must be configured") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenValidity,
        @NotNull Duration refreshTokenValidity) {}
