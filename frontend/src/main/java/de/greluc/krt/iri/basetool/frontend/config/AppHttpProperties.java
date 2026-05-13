package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Type-safe App Http configuration properties. */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.http")
public class AppHttpProperties {
  @NotNull private Duration connectTimeout = Duration.ofSeconds(3);

  @NotNull private Duration responseTimeout = Duration.ofSeconds(5);

  @NotNull private Duration readTimeout = Duration.ofSeconds(5);

  @NotNull private Duration writeTimeout = Duration.ofSeconds(5);
}
