package de.greluc.krt.iri.basetool.frontend.config;

import java.time.Duration;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/** Spring configuration for Locale. */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

  /**
   * Cookie-based {@link LocaleResolver}: persists the user's locale in the {@code KRT_LOCALE}
   * cookie (default German, 90-day (3-month) max-age, path {@code /}).
   */
  @Bean
  public LocaleResolver localeResolver() {
    CookieLocaleResolver clr = new CookieLocaleResolver("KRT_LOCALE");
    clr.setDefaultLocale(Locale.GERMAN);
    clr.setCookieMaxAge(Duration.ofDays(90));
    clr.setCookiePath("/");
    return clr;
  }

  /** Interceptor switching the locale when the request carries a {@code ?lang=…} query param. */
  @Bean
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
    lci.setParamName("lang");
    return lci;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
  }
}
