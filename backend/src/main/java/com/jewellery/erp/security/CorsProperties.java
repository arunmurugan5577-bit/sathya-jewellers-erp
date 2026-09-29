package com.jewellery.erp.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration, bound from {@code app.cors.*}.
 *
 * <p>{@code allowedOrigins} is an explicit list - a wildcard is rejected at
 * start-up by {@link SecurityConfig} so that a production deployment cannot
 * accidentally open the API to every origin.
 *
 * <p>{@code exposedHeaders} names the response headers a cross-origin page may
 * read. Browsers hide everything else, including {@code Content-Disposition},
 * which is where a downloaded report carries its filename.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        Long maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        allowedMethods = allowedMethods == null
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null
                ? List.of("Authorization", "Content-Type", "Accept")
                : List.copyOf(allowedHeaders);
        exposedHeaders = exposedHeaders == null ? List.of("Content-Disposition") : List.copyOf(exposedHeaders);
        maxAge = maxAge == null ? 3600L : maxAge;
    }

    public boolean containsWildcardOrigin() {
        return allowedOrigins.stream().anyMatch(origin -> origin.contains("*"));
    }
}
