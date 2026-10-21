package com.pavti.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html with an "Authorize" button for the Bearer token. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pavtiOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Pavti API").version("1.0")
                        .description("Restaurant billing: auth, menu, tables, bills, payments, reports"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
