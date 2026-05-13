package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app")
public class AppBackendProperties {
  /** Base URL of the backend API used by the frontend. */
  @NotBlank @URL private String backendUrl;
}
