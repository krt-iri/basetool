package de.greluc.krt.iri.basetool.backend.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule normalizedStringModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new NormalizedStringDeserializer());
        return module;
    }
}
