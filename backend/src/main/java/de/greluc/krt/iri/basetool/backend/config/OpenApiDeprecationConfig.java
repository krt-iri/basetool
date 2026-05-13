package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiDeprecationConfig {

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
