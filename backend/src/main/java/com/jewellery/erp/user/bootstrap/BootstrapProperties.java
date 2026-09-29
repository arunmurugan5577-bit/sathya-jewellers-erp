package com.jewellery.erp.user.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Start-up bootstrap configuration, bound from {@code app.bootstrap.*}.
 *
 * <p>These values arrive from the environment and are never written to a
 * configuration file that is committed.
 *
 * @param admin the initial administrator to create when none exists
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(AdminProperties admin) {

    /**
     * @param enabled set {@code INITIAL_ADMIN_ENABLED=false} to switch the runner
     *     off entirely once the shop is live
     */
    public record AdminProperties(
            boolean enabled, String username, String password, String email, String fullName) {}
}
