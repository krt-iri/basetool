package de.greluc.krt.iri.basetool.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Replaces Spring Boot's auto-configured Jackson {@link ObjectMapper} with one that applies {@link
 * NormalizedStringDeserializer} to every JSON {@code String}.
 *
 * <p>The deserializer trims and collapses internal whitespace exactly like {@link
 * GlobalBindingAdvice} does for form posts, so a JSON request body and an x-www-form-urlencoded
 * body cannot lead to subtly different stored values (e.g. one with trailing spaces, the other
 * without). {@code @Primary} ensures the project's mapper wins over the auto-configured one
 * wherever both are eligible (typically: WebMvc's HTTP message converters).
 */
@Configuration
public class JacksonConfig {

  /**
   * Returns the project's primary {@link ObjectMapper} with the normalized-string module
   * registered; used wherever Spring's auto-wired mapper would otherwise win.
   *
   * @return the project's primary {@link ObjectMapper} with the normalized-string module
   *     registered; used wherever Spring's auto-wired mapper would otherwise win
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(normalizedStringModule());
    return mapper;
  }

  /**
   * Returns Jackson module that installs {@link NormalizedStringDeserializer} for every JSON string
   * field, exposed as a bean so tests can register it on their own mappers.
   *
   * @return Jackson module that installs {@link NormalizedStringDeserializer} for every JSON string
   *     field, exposed as a bean so tests can register it on their own mappers
   */
  @Bean
  public SimpleModule normalizedStringModule() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(String.class, new NormalizedStringDeserializer());
    return module;
  }
}
