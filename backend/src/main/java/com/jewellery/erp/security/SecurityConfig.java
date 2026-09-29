package com.jewellery.erp.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central security configuration.
 *
 * <p>Shape of the model:
 *
 * <ul>
 *   <li><b>Stateless.</b> No session, no CSRF token - the API is only ever
 *       called with a bearer token, never with an ambient cookie, so there is no
 *       CSRF surface to protect.
 *   <li><b>Authorities are permissions.</b> Endpoints are guarded with
 *       {@code @PreAuthorize("hasAuthority('MODULE_ACTION')")} at the service or
 *       controller boundary; this class only separates "public" from
 *       "authenticated".
 *   <li><b>Deny by default.</b> {@code anyRequest().authenticated()} means a new
 *       endpoint is protected the moment it is written, even if its author
 *       forgets the annotation.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize / @PostAuthorize
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Endpoints reachable without a token. Kept deliberately short. */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/auth/login", "/api/auth/refresh", "/actuator/health", "/actuator/health/**",
        // Not a real endpoint: the container forwards here when a request dies
        // outside the handler (content negotiation, a servlet-level error). The
        // original request has already been through this chain, so guarding the
        // forward adds no security - it only replaces the true status with 401
        // and signs the user out over something that was never an auth problem.
        "/error"
    };

    /**
     * Documentation endpoints. springdoc is disabled outside the dev profile, so
     * these resolve to 404 in production even though the rule permits them.
     */
    private static final String[] DOCUMENTATION_ENDPOINTS = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;
    private final Environment environment;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsProperties corsProperties,
            Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Safe to disable: the API never authenticates from a cookie.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                        // Deny by default: anything not listed above needs a token.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt at strength 12.
     *
     * <p>Higher than the Spring default of 10 because this is a low-volume
     * internal ERP: roughly a quarter second per login is invisible to the user
     * and meaningfully raises the cost of an offline attack on a stolen hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // An unknown username is reported as BadCredentials so the login response
        // cannot be used to enumerate accounts. DisabledException and LockedException
        // still surface - those the user needs to act on.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * CORS for the Angular client.
     *
     * <p>A wildcard origin is rejected outright when the {@code prod} profile is
     * active: an ERP holding stock and customer data must name the hosts that may
     * call it.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        boolean production = List.of(environment.getActiveProfiles()).contains("prod");
        if (production && (corsProperties.allowedOrigins().isEmpty() || corsProperties.containsWildcardOrigin())) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must list explicit origins in production; wildcards are not permitted.");
        }
        if (corsProperties.containsWildcardOrigin()) {
            log.warn("CORS is configured with a wildcard origin. Acceptable for local development only.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(corsProperties.allowedHeaders());
        configuration.setExposedHeaders(corsProperties.exposedHeaders());
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
