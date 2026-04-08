package de.greluc.krt.iri.basetool.backend.config;

import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiCachingConfig {

    @Bean
    public OpenApiCustomizer cachingOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            item.readOperationsMap().forEach((httpMethod, operation) -> {
                if (httpMethod.name().equalsIgnoreCase("GET")) {
                    ApiResponses responses = operation.getResponses();
                    // Document 304 Not Modified
                    responses.addApiResponse("304", new ApiResponse().description("Not Modified (ETag/If-None-Match)"));

                    // Add standard caching headers to 200 response
                    ApiResponse ok = responses.computeIfAbsent("200", k -> new ApiResponse().description("OK"));
                    if (ok.getHeaders() == null) {
                        ok.setHeaders(new java.util.LinkedHashMap<>());
                    }
                    ok.getHeaders().putIfAbsent("ETag", new Header().description("Entity Tag for conditional requests").schema(new StringSchema()));
                    ok.getHeaders().putIfAbsent("Cache-Control", new Header().description("Caching policy (e.g. no-cache for API responses)").schema(new StringSchema()));
                }
            });
        });
    }
}
