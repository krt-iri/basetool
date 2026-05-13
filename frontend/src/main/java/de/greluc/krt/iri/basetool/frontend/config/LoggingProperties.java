package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for structured logging, MDC correlation, slow-request detection and
 * slow WebClient call detection in the frontend module. Bound under {@code app.logging.*} in
 * {@code application*.yml}. Invalid values fail the context start early.
 */
@Getter
@Setter
@ToString
@Validated
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

    /** HTTP header used to accept an inbound correlation id and echo the effective one back. */
    @NotBlank
    private String correlationIdHeader = "X-Correlation-Id";

    /** MDC key for the correlation id. Must match the {@code %X{correlationId}} pattern. */
    @NotBlank
    private String correlationIdMdcKey = "correlationId";

    /** MDC key for the JWT {@code sub} claim. Intentionally no emails/names. */
    @NotBlank
    private String userIdMdcKey = "userId";

    /** Requests slower than this are logged at WARN by {@code RequestLoggingFilter}. */
    @Min(0)
    private long slowRequestThresholdMs = 2000L;

    /** WebClient calls slower than this are logged at WARN by {@code WebClientLoggingFilter}. */
    @Min(0)
    private long slowBackendCallThresholdMs = 1500L;

    /** Feature flag for structured (JSON) logging, activated in {@code logback-spring.xml}. */
    private boolean structuredEnabled = false;
}
