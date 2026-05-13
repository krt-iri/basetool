package de.greluc.krt.iri.basetool.frontend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/** Spring configuration for E Tag. */
@Configuration
public class ETagConfig {

  /**
   * Registers Spring's {@link ShallowEtagHeaderFilter} for all paths at near-highest precedence so
   * {@code ETag} / {@code If-None-Match} 304 short-circuits happen before MVC builds the body.
   */
  @Bean
  public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
    FilterRegistrationBean<ShallowEtagHeaderFilter> filter = new FilterRegistrationBean<>();
    filter.setFilter(new ShallowEtagHeaderFilter());
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    filter.addUrlPatterns("/*");
    return filter;
  }
}
