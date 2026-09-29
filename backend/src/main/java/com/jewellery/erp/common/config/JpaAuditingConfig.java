package com.jewellery.erp.common.config;

import com.jewellery.erp.security.AuditorAwareImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.time.Instant;
import java.util.Optional;

/**
 * Enables the {@code @CreatedDate} / {@code @CreatedBy} / {@code @LastModified*}
 * annotations used by {@code AuditableEntity}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();
    }

    /** All audit timestamps are UTC instants, independent of the server time zone. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
