package com.jewellery.erp.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;

/**
 * Supplies {@code created_by} / {@code updated_by}.
 *
 * <p>Falls back to {@link SecurityUtils#SYSTEM_USER} for writes that happen
 * outside a request - start-up bootstrap and scheduled maintenance - so the
 * audit columns are never null for a row the application created.
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
    }
}
