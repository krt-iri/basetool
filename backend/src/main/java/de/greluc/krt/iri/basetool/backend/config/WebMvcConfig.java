package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.interceptor.DeprecationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers MVC interceptors. Currently only attaches {@link DeprecationInterceptor} so deprecated
 * endpoints emit the {@code Deprecation}/{@code Sunset}/{@code Link} response headers documented in
 * CLAUDE.md. Kept as a dedicated configuration class so future interceptor additions land here
 * rather than scattered across feature configs.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final DeprecationInterceptor deprecationInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(deprecationInterceptor);
  }
}
