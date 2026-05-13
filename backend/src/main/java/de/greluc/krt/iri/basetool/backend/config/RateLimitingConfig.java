package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.filter.RateLimitingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitingConfig {

  @Bean
  public RateLimitingFilter rateLimitingFilter(
      RateLimitProperties properties, AppProblemProperties problemProperties) {
    return new RateLimitingFilter(properties, problemProperties);
  }

  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>
      rateLimitingFilterRegistration(RateLimitingFilter filter) {
    org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>
        registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
