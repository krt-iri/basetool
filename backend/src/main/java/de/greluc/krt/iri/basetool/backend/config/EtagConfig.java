package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class EtagConfig {

  @Bean
  public ShallowEtagHeaderFilter shallowEtagFilter() {
    return new ShallowEtagHeaderFilter();
  }

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
