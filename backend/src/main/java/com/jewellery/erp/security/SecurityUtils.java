package com.jewellery.erp.security;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Read-only access to the currently authenticated principal. */
public final class SecurityUtils {

    /** Used for rows written outside a request, e.g. the bootstrap administrator. */
    public static final String SYSTEM_USER = "system";

    private SecurityUtils() {}

    /**
     * Whether the caller is authenticated at all.
     *
     * <p>Deliberately independent of {@link #currentPrincipal()}: a request can be
     * authenticated by something other than this application's JWT filter - a
     * test, or a future SSO integration - and "not one of our principals" must
     * not be mistaken for "not signed in". Getting that wrong turns a 403 into a
     * 401 and tells the user to log in again when their session is perfectly
     * valid.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /** The caller's name from any authentication mechanism, for logging. */
    public static Optional<String> authenticatedName() {
        if (!isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static Optional<String> currentUsername() {
        return currentPrincipal().map(UserPrincipal::username);
    }

    public static Optional<Long> currentUserId() {
        return currentPrincipal().map(UserPrincipal::id);
    }

    public static boolean hasPermission(String permissionCode) {
        return currentPrincipal()
                .map(principal -> principal.permissions().contains(permissionCode))
                .orElse(false);
    }
}
