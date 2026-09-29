package com.jewellery.erp.auth.service;

import com.jewellery.erp.auth.dto.AuthenticatedUserDto;
import com.jewellery.erp.auth.dto.LoginRequest;
import com.jewellery.erp.auth.dto.LoginResponse;
import com.jewellery.erp.auth.entity.RefreshToken;
import com.jewellery.erp.security.JwtTokenProvider;
import com.jewellery.erp.security.SecurityUtils;
import com.jewellery.erp.security.UserPrincipal;
import com.jewellery.erp.user.entity.User;
import com.jewellery.erp.user.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, token refresh and logout.
 *
 * <p>Nothing in this class logs a password, a token or a token hash. Failed
 * logins are logged with the username only, which is what an administrator
 * needs in order to investigate.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Verifies credentials and issues a token pair.
     *
     * @throws BadCredentialsException when the username or password is wrong
     * @throws DisabledException when the account is deactivated
     * @throws LockedException when the account is locked
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for username '{}'", request.username());
            throw ex;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Load the managed entity so the refresh token can reference it and the
        // last-login stamp is written inside this transaction.
        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
        user.setLastLoginAt(Instant.now());

        log.info("User '{}' signed in", principal.username());
        return issueTokens(principal, user);
    }

    /**
     * Exchanges a refresh token for a fresh token pair.
     *
     * <p>The presented token is revoked as part of the exchange (rotation), and
     * the user's current status and permissions are re-read from the database -
     * this is the point at which a deactivation or a permission change becomes
     * effective.
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenService
                .findUsable(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("The refresh token is invalid or has expired."));

        User user = userRepository
                .findWithAuthoritiesById(stored.getUser().getId())
                .orElseThrow(() -> new BadCredentialsException("The refresh token is invalid or has expired."));

        if (!user.canAuthenticate()) {
            // The account was deactivated or locked while the session was live.
            refreshTokenService.revokeAllForUser(user.getId());
            throw new DisabledException("This account is no longer active.");
        }

        refreshTokenService.revoke(stored);
        return issueTokens(UserPrincipal.from(user), user);
    }

    /** Ends the session the presented refresh token belongs to. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeByRawToken(rawRefreshToken);
        }
        SecurityUtils.currentUsername().ifPresent(username -> log.info("User '{}' signed out", username));
    }

    /** Ends every session of the signed-in user. */
    @Transactional
    public void logoutEverywhere() {
        SecurityUtils.currentUserId().ifPresent(refreshTokenService::revokeAllForUser);
    }

    /** The signed-in user, rebuilt from the token claims. */
    @Transactional(readOnly = true)
    public AuthenticatedUserDto currentUser() {
        UserPrincipal principal = SecurityUtils.currentPrincipal()
                .orElseThrow(() -> new BadCredentialsException("No authenticated user."));
        return AuthenticatedUserDto.from(principal);
    }

    private LoginResponse issueTokens(UserPrincipal principal, User user) {
        JwtTokenProvider.IssuedToken accessToken = tokenProvider.createAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user);

        return LoginResponse.of(
                accessToken.token(),
                accessToken.expiresInSeconds(),
                accessToken.expiresAt(),
                refreshToken,
                AuthenticatedUserDto.from(principal));
    }
}
