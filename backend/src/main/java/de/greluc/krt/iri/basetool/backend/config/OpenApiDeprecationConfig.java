package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * SpringDoc customizer that mirrors the project's {@link ApiDeprecation} + {@code @Deprecated}
 * metadata into the generated OpenAPI document.
 *
 * <p>The {@link de.greluc.krt.iri.basetool.backend.interceptor.DeprecationInterceptor} already
 * emits the runtime headers; this customizer keeps the static spec in sync so SwaggerUI shows a
 * "deprecated" badge on the operation and adds a {@code **DEPRECATED**} block to the description
 * with the sunset date and the recommended replacement path.
 */
@Configuration
public class OpenApiDeprecationConfig {

  /**
   * @return SpringDoc operation customizer that annotates deprecated endpoints in the OpenAPI doc,
   *     respecting both the project-specific {@link ApiDeprecation} and the JDK {@code @Deprecated}
   *     on the handler method or its declaring class
   */
  @Bean
  public OperationCustomizer deprecationCustomizer() {
    return (Operation operation, HandlerMethod handlerMethod) -> {
      ApiDeprecation deprecation = handlerMethod.getMethodAnnotation(ApiDeprecation.class);
      boolean isDeprecated = handlerMethod.hasMethodAnnotation(Deprecated.class);

      if (deprecation == null) {
        deprecation = handlerMethod.getBeanType().getAnnotation(ApiDeprecation.class);
      }
      if (!isDeprecated) {
        isDeprecated = handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class);
      }

      if (isDeprecated || deprecation != null) {
        operation.setDeprecated(true);

        if (deprecation != null) {
          StringBuilder desc = new StringBuilder();
          if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
            desc.append(operation.getDescription()).append("\n\n");
          }
          desc.append("**DEPRECATED**");
          if (!deprecation.sunset().isEmpty()) {
            desc.append("\n- Sunset Date: ").append(deprecation.sunset());
          }
          if (!deprecation.replacement().isEmpty()) {
            desc.append("\n- Replacement: `").append(deprecation.replacement()).append("`");
          }
          operation.setDescription(desc.toString().trim());
        }
      }
      return operation;
    };
  }
}
