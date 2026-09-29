package com.jewellery.erp.user.bootstrap;

import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.role.entity.Role;
import com.jewellery.erp.role.repository.RoleRepository;
import com.jewellery.erp.user.entity.User;
import com.jewellery.erp.user.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from environment variables.
 *
 * <p>Why not a seed migration: a SQL file containing even a BCrypt hash is a
 * credential in version control, and every installation would ship with the same
 * one. Instead the account is created once, at start-up, from
 * {@code INITIAL_ADMIN_USERNAME} / {@code INITIAL_ADMIN_PASSWORD} /
 * {@code INITIAL_ADMIN_EMAIL}, and the password is hashed before it is stored.
 *
 * <p>The runner is inert once any active administrator exists, so leaving the
 * variables set on a running installation changes nothing. The password is never
 * logged.
 */
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public InitialAdminBootstrap(
            BootstrapProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BootstrapProperties.AdminProperties admin = properties.admin();

        if (admin == null || !admin.enabled()) {
            return;
        }
        if (userRepository.existsActiveAdministrator()) {
            log.debug("An administrator already exists; skipping bootstrap.");
            return;
        }

        String username = StringNormalizer.trimToNull(admin.username());
        String password = admin.password();

        if (username == null || password == null || password.isBlank()) {
            // Loud, because the installation is currently unusable: there is no
            // administrator and no way to create one through the API.
            log.error("""
                    No administrator exists and INITIAL_ADMIN_USERNAME / INITIAL_ADMIN_PASSWORD are not set.
                    Nobody can sign in. Set both variables and restart - see README > Default administrator.""");
            return;
        }
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            log.error("INITIAL_ADMIN_PASSWORD is shorter than {} characters; the administrator was not created.",
                    MINIMUM_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.error("INITIAL_ADMIN_USERNAME '{}' already exists but is not an active administrator; "
                    + "no account was created.", username);
            return;
        }

        Role adminRole = roleRepository
                .findByName(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_ADMIN is missing. Flyway migrations have not been applied."));

        User user = new User();
        user.setUsername(username);
        user.setFullName(StringNormalizer.normalizeName(admin.fullName()) == null
                ? "System Administrator"
                : StringNormalizer.normalizeName(admin.fullName()));
        user.setEmail(StringNormalizer.normalizeLower(admin.email()));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setAccountLocked(false);
        // The bootstrap password came from an environment variable, which is
        // visible to anyone who can read the deployment configuration.
        user.setMustChangePassword(true);
        user.setRoles(new LinkedHashSet<>(Set.of(adminRole)));

        userRepository.save(user);

        log.info("""

                ============================================================
                 Initial administrator '{}' created.
                 Sign in with the password from INITIAL_ADMIN_PASSWORD; you
                 will be asked to change it immediately. Remove the variable
                 from the environment afterwards.
                ============================================================""", username);
    }
}
