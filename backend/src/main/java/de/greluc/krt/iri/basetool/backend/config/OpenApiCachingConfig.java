package de.greluc.krt.iri.basetool.backend.config;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc customizer that documents the project's HTTP caching contract in the generated OpenAPI
 * spec.
 *
 * <p>Every {@code GET} endpoint goes through {@link ETagConfig#shallowEtagFilter} and therefore
 * supports {@code If-None-Match} / 304 responses; the customizer adds a {@code 304 Not Modified}
 * entry to the responses map and decorates the {@code 200} response with the {@code ETag} and
 * {@code Cache-Control} header descriptions so client SDKs generated from the spec know they can
 * implement conditional GET.
 */
@Configuration
public class OpenApiCachingConfig {

  /**
   * @return OpenAPI customizer that injects the {@code 304}/{@code ETag}/{@code Cache-Control}
   *     metadata on every {@code GET} operation
   */
  @Bean
  public OpenApiCustomizer cachingOpenApiCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, item) -> {
                  item.readOperationsMap()
                      .forEach(
                          (httpMethod, operation) -> {
                            if (httpMethod.name().equalsIgnoreCase("GET")) {
                              ApiResponses responses = operation.getResponses();
                              // Document 304 Not Modified
                              responses.addApiResponse(
                                  "304",
                                  new ApiResponse()
                                      .description("Not Modified (ETag/If-None-Match)"));

                              // Add standard caching headers to 200 response
                              ApiResponse ok =
                                  responses.computeIfAbsent(
                                      "200", k -> new ApiResponse().description("OK"));
                              if (ok.getHeaders() == null) {
                                ok.setHeaders(new java.util.LinkedHashMap<>());
                              }
                              ok.getHeaders()
                                  .putIfAbsent(
                                      "ETag",
                                      new Header()
                                          .description("Entity Tag for conditional requests")
                                          .schema(new StringSchema()));
                              ok.getHeaders()
                                  .putIfAbsent(
                                      "Cache-Control",
                                      new Header()
                                          .description(
                                              "Caching policy (e.g. no-cache for API responses)")
                                          .schema(new StringSchema()));
                            }
                          });
                });
  }
}
