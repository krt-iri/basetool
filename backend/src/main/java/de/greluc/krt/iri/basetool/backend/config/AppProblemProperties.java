package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code app.problems.*}.
 *
 * <p>Centralizes the base URI used to build RFC&nbsp;7807 {@code type} values (e.g. {@code
 * https://profit-base.online/problems/not-found}). Keeping the prefix here lets us repoint
 * problem-type URIs per environment without hunting through {@code GlobalExceptionHandler} for
 * hardcoded strings.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.problems")
public class AppProblemProperties {
  /** Base URI for Problem Detail types. */
  @NotBlank private String baseUri = "https://profit-base.online/problems/";
}
