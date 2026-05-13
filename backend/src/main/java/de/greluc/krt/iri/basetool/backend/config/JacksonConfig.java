package de.greluc.krt.iri.basetool.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(normalizedStringModule());
    return mapper;
  }

  @Bean
  public SimpleModule normalizedStringModule() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(String.class, new NormalizedStringDeserializer());
    return module;
  }
}
