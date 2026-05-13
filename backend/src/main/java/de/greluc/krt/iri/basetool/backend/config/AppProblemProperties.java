package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.problems")
public class AppProblemProperties {
  /** Base URI for Problem Detail types */
  @NotBlank private String baseUri = "https://iri-base.org/problems/";
}
