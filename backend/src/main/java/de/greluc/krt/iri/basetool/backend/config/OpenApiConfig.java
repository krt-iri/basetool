package de.greluc.krt.iri.basetool.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI document for the backend module.
 *
 * <p>Sets the spec version, declares the {@code bearer-jwt} security scheme so generated clients
 * know every authenticated endpoint expects a Keycloak JWT, and marks {@code bearer-jwt} as the
 * default security requirement so endpoints without an explicit override inherit it. The project
 * ships only the OpenAPI document ({@code openapi.json}) — Swagger UI is not bundled.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Returns the {@link OpenAPI} root document used by SpringDoc to generate {@code openapi.json}.
   *
   * @return the {@link OpenAPI} root document used by SpringDoc to generate {@code openapi.json}
   */
  @Bean
  public OpenAPI krtOpenApi() {
    return new OpenAPI()
        .openapi("3.1.1")
        .info(
            new Info()
                .title("KRT Basetool Backend API")
                .version("1.0")
                .description("API documentation for the KRT Basetool Backend application."))
        .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer-jwt",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
