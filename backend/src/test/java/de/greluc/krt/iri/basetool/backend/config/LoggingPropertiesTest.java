package de.greluc.krt.iri.basetool.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binding contract for {@link LoggingProperties}.
 *
 * <p>Defaults must be stable (used by logback-spring.xml pattern) and validation must fail fast on
 * invalid values so the context never starts with a broken logging configuration.
 */
class LoggingPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withUserConfiguration(Config.class);

  @Configuration
  @EnableConfigurationProperties(LoggingProperties.class)
  @ConfigurationPropertiesScan
  static class Config {}

  @Test
  void defaults_ShouldMatchLogbackPatternExpectations() {
    runner.run(
        context -> {
          LoggingProperties props = context.getBean(LoggingProperties.class);

          // Given/When: defaults loaded
          // Then: match the %X{correlationId}/%X{userId} placeholders in logback-spring.xml
          assertThat(props.getCorrelationIdHeader()).isEqualTo("X-Correlation-Id");
          assertThat(props.getCorrelationIdMdcKey()).isEqualTo("correlationId");
          assertThat(props.getUserIdMdcKey()).isEqualTo("userId");
          assertThat(props.getSlowRequestThresholdMs()).isEqualTo(2000L);
          assertThat(props.isStructuredEnabled()).isFalse();
        });
  }

  @Test
  void invalidSlowRequestThreshold_ShouldFailContextStart() {
    runner
        .withPropertyValues("app.logging.slow-request-threshold-ms=-1")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void blankCorrelationHeader_ShouldFailContextStart() {
    runner
        .withPropertyValues("app.logging.correlation-id-header=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void customValues_ShouldOverrideDefaults() {
    runner
        .withPropertyValues(
            "app.logging.correlation-id-header=X-Trace-Id",
            "app.logging.slow-request-threshold-ms=5000",
            "app.logging.structured-enabled=true")
        .run(
            context -> {
              LoggingProperties p = context.getBean(LoggingProperties.class);
              assertThat(p.getCorrelationIdHeader()).isEqualTo("X-Trace-Id");
              assertThat(p.getSlowRequestThresholdMs()).isEqualTo(5000L);
              assertThat(p.isStructuredEnabled()).isTrue();
            });
  }

  @Test
  void toString_ShouldNotLeakSensitiveInformation() {
    LoggingProperties p = new LoggingProperties();
    // Given/When
    String s = p.toString();
    // Then: deterministic content, no password/token-like fields
    assertThat(s).contains("correlationIdHeader", "slowRequestThresholdMs");
    assertThat(s).doesNotContain("password", "token", "secret");
  }

  /** Guard against accidental removal of the setter API used by Spring binding. */
  @Test
  void setters_ShouldBeCallableByBinder() {
    LoggingProperties p = new LoggingProperties();
    assertThatThrownBy(
            () -> {
              p.setSlowRequestThresholdMs(-5L);
              // no-op: value invalidity is enforced by @Validated at context level, not here
              if (p.getSlowRequestThresholdMs() < 0) {
                throw new IllegalStateException("validator would reject");
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }
}
