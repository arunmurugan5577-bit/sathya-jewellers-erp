package com.jewellery.erp.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata and the bearer-token security scheme.
 *
 * <p>Swagger UI is enabled only by the {@code dev} profile
 * (see {@code application-dev.yml}); the production profile disables both the
 * UI and the {@code /v3/api-docs} endpoint.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI jewelleryErpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jewellery Shop ERP API")
                        .version("1.0.0")
                        .description("""
                                REST API for the Jewellery Shop ERP.

                                Authenticate with POST /api/auth/login, then send the returned
                                access token as `Authorization: Bearer <token>`.

                                Every endpoint is guarded by a MODULE_ACTION permission - the
                                required permission is listed in each operation description.
                                """)
                        .contact(new Contact().name("Jewellery ERP"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token issued by /api/auth/login")));
    }
}
