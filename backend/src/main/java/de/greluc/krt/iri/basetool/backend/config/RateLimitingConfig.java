package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.filter.RateLimitingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link RateLimitingFilter} and registers it for all URLs at very high precedence so
 * abusive callers are rejected before any heavier filter (authentication, ETag, controller
 * dispatch) gets a chance to run.
 */
@Configuration
public class RateLimitingConfig {

  /**
   * Returns the {@link RateLimitingFilter} bean injected into the servlet container registration
   * below.
   *
   * @param properties typed configuration with bucket capacity, refill rate, path patterns and
   *     trusted proxies
   * @param problemProperties RFC&nbsp;7807 base URI used in the 429 problem-detail response body
   * @return the {@link RateLimitingFilter} bean injected into the servlet container registration
   *     below
   */
  @Bean
  public RateLimitingFilter rateLimitingFilter(
      RateLimitProperties properties, AppProblemProperties problemProperties) {
    return new RateLimitingFilter(properties, problemProperties);
  }

  /**
   * Registers the {@link RateLimitingFilter} for the entire URI space ({@code /*}) very early in
   * the filter chain (highest precedence + 10) so a rejected request never reaches downstream
   * components.
   *
   * @param filter the rate-limiting filter created by {@link #rateLimitingFilter}
   * @return Servlet registration with the order and URL patterns set
   */
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
