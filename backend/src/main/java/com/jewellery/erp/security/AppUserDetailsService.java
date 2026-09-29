package com.jewellery.erp.security;

import com.jewellery.erp.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user and their effective authorities for the authentication manager.
 *
 * <p>Login and refresh load the whole user. Ordinary requests take their
 * authorities from the JWT claims and only ask {@link #isActive(Long)} whether
 * the account is still switched on.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Whether the account behind a token is still active.
     *
     * <p>A signed token stays valid until it expires, so without this check a
     * user who has just been deactivated keeps working for the rest of their
     * access token's life. Deactivating is supposed to end their sessions, and
     * one indexed read per request is a fair price for it being true.
     *
     * @return false when the account is switched off, or no longer exists
     */
    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        return userId != null && userRepository.findActiveById(userId).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository
                .findWithAuthoritiesByUsernameIgnoreCase(username)
                .map(UserPrincipal::from)
                // The message is generic on purpose: a distinct "no such user"
                // response would let an attacker enumerate valid usernames.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."));
    }
}
