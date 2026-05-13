package de.greluc.krt.iri.basetool.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiProblemDetailsConfig {

  @Bean
  public OpenApiCustomizer problemDetailsCustomizer() {
    return this::customizeOpenApi;
  }

  private void customizeOpenApi(OpenAPI openApi) {
    ensureProblemDetailSchema(openApi);
    if (openApi.getPaths() == null) {
      return;
    }
    openApi
        .getPaths()
        .values()
        .forEach(
            pathItem -> {
              pathItem
                  .readOperations()
                  .forEach(
                      operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                          return;
                        }
                        addProblemResponse(responses, "400", "Bad Request");
                        addProblemResponse(responses, "401", "Unauthorized");
                        addProblemResponse(responses, "403", "Forbidden");
                        addProblemResponse(responses, "404", "Not Found");
                        addProblemResponse(responses, "409", "Conflict");
                        addProblemResponse(responses, "500", "Internal Server Error");
                      });
            });
  }

  private void addProblemResponse(ApiResponses responses, String code, String description) {
    Content content =
        new Content()
            .addMediaType(
                "application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")));
    responses.addApiResponse(code, new ApiResponse().description(description).content(content));
  }

  private void ensureProblemDetailSchema(OpenAPI openApi) {
    Components components = openApi.getComponents();
    if (components == null) {
      components = new Components();
      openApi.setComponents(components);
    }
    Map<String, Schema> schemas = components.getSchemas();
    if (schemas == null || !schemas.containsKey("ProblemDetail")) {
      @SuppressWarnings("rawtypes")
      Schema<?> problem =
          new Schema<>()
              .type("object")
              .addProperty("type", new Schema<String>().type("string").format("uri"))
              .addProperty("title", new Schema<String>().type("string"))
              .addProperty("status", new Schema<Integer>().type("integer").format("int32"))
              .addProperty("detail", new Schema<String>().type("string"))
              .addProperty("instance", new Schema<String>().type("string").format("uri"))
              .addProperty("errors", new Schema<Map<String, String>>().type("object"));
      components.addSchemas("ProblemDetail", problem);
    }
  }
}
