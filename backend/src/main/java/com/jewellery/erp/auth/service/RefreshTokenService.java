package com.jewellery.erp.auth.service;

import com.jewellery.erp.auth.entity.RefreshToken;
import com.jewellery.erp.auth.repository.RefreshTokenRepository;
import com.jewellery.erp.security.JwtProperties;
import com.jewellery.erp.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, validates and revokes refresh tokens.
 *
 * <p>The token itself is 256 bits of {@link SecureRandom} output - opaque, with
 * no structure an attacker can exploit - and only its SHA-256 hash is stored.
 * Rotation is enforced: redeeming a token revokes it and issues a new one, so a
 * stolen token is usable at most once and its reuse is detectable.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final Duration EXPIRED_TOKEN_RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenValidity;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
    }

    /**
     * Creates a new refresh token for a user.
     *
     * @return the raw token - the only moment it exists in clear text; it is
     *     returned to the caller and never stored or logged
     */
    @Transactional
    public String issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant now = Instant.now();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setIssuedAt(now);
        entity.setExpiresAt(now.plus(refreshTokenValidity));
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /** Looks up a usable (unrevoked, unexpired) token by its raw value. */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findUsable(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(RefreshToken::isUsable);
    }

    /** Revokes a single token. Used by logout and by rotation on refresh. */
    @Transactional
    public void revoke(RefreshToken token) {
        if (!token.isRevoked()) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    /** Revokes a token by its raw value, ignoring one that is already gone. */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(this::revoke);
    }

    /** Ends every session of a user. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        if (revoked > 0) {
            log.info("Revoked {} active session(s) for user id {}", revoked, userId);
        }
    }

    /** Nightly clean-up so the table does not grow without bound. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(EXPIRED_TOKEN_RETENTION));
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JRE spec; this cannot happen.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
