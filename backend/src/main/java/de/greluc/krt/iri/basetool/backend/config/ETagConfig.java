package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Wires Spring's {@link ShallowEtagHeaderFilter} so every {@code GET} response gets an {@code ETag}
 * header derived from the response body. Clients that resend the previous {@code ETag} value in
 * {@code If-None-Match} get a fast 304 without re-rendering the JSON payload on the server.
 * Registered very early (highest precedence + 10) so the short-circuit happens before downstream
 * filters spend any CPU.
 */
@Configuration
public class ETagConfig {

  /**
   * @return Spring's shallow ETag filter bean, kept as a separate bean so other configurations can
   *     wrap or replace it without rebuilding the registration
   */
  @Bean
  public ShallowEtagHeaderFilter shallowEtagFilter() {
    return new ShallowEtagHeaderFilter();
  }

  /**
   * Registers {@link ShallowEtagHeaderFilter} for all URL patterns ({@code /*}) at very high
   * precedence so conditional 304 responses are returned before the request reaches the rate
   * limiter or security filter chain.
   *
   * @param shallowEtagFilter the filter bean to register
   * @return Servlet registration with order and URL patterns set
   */
  @Bean
  public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter(
      ShallowEtagHeaderFilter shallowEtagFilter) {
    FilterRegistrationBean<ShallowEtagHeaderFilter> filter = new FilterRegistrationBean<>();
    filter.setFilter(shallowEtagFilter);
    // Ensure ETag is applied early so conditional requests can be short-circuited
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    filter.addUrlPatterns("/*");
    return filter;
  }
}
