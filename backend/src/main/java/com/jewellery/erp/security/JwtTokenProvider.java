package com.jewellery.erp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the short-lived access token.
 *
 * <p>The token carries the user id, display name, roles and effective permission
 * codes, which lets every subsequent request be authorised without touching the
 * database. The trade-off is explicit: a permission or status change takes
 * effect on the user's next token refresh (at most
 * {@code app.jwt.access-token-validity}). Operations that must apply
 * immediately - deactivation, password reset, permission change - revoke the
 * user's refresh tokens so the session cannot be extended.
 *
 * <p>Nothing in this class is ever logged: not the secret, not the token.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_FULL_NAME = "name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final int MINIMUM_KEY_BYTES = 32; // HS256 requires >= 256 bits

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenValidity;

    public JwtTokenProvider(JwtProperties properties) {
        this.signingKey = buildKey(properties.secret());
        this.issuer = properties.issuer();
        this.accessTokenValidity = properties.accessTokenValidity();
    }

    /**
     * Accepts either a Base64 encoded key (recommended, and what the README
     * generates) or a sufficiently long raw passphrase. A key shorter than 256
     * bits fails fast at start-up rather than weakening every token silently.
     */
    private static SecretKey buildKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least 256 bits. "
                            + "Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Issues an access token for an authenticated principal. */
    public IssuedToken createAccessToken(UserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenValidity);

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(principal.getUsername())
                .claim(CLAIM_USER_ID, principal.id())
                .claim(CLAIM_FULL_NAME, principal.fullName())
                .claim(CLAIM_ROLES, List.copyOf(principal.roles()))
                .claim(CLAIM_PERMISSIONS, List.copyOf(principal.permissions()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt, accessTokenValidity.toSeconds());
    }

    /**
     * Verifies signature, issuer and expiry, then rebuilds the principal from the
     * claims.
     *
     * @return the principal, or empty when the token is absent, malformed,
     *     tampered with or expired
     */
    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get(CLAIM_USER_ID, Number.class) == null
                    ? null
                    : claims.get(CLAIM_USER_ID, Number.class).longValue();

            return Optional.of(UserPrincipal.fromClaims(
                    userId,
                    claims.getSubject(),
                    claims.get(CLAIM_FULL_NAME, String.class),
                    stringSet(claims, CLAIM_ROLES),
                    stringSet(claims, CLAIM_PERMISSIONS)));

        } catch (ExpiredJwtException expired) {
            log.debug("Rejected an expired access token");
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException invalid) {
            // Message only - never the token itself.
            log.debug("Rejected an invalid access token: {}", invalid.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof List<?> list) {
            return new LinkedHashSet<>((List<String>) list);
        }
        return Set.of();
    }

    /** An issued access token together with its expiry metadata. */
    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {}
}
