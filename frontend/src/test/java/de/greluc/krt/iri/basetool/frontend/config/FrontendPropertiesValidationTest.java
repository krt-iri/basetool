package de.greluc.krt.iri.basetool.frontend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FrontendPropertiesValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(TestConfig.class);

  @Configuration
  @EnableConfigurationProperties({AppBackendProperties.class, AppHttpProperties.class})
  static class TestConfig {
    @Bean
    LocalValidatorFactoryBean validator() {
      return new LocalValidatorFactoryBean();
    }
  }

  @Test
  void shouldFailWhenBackendUrlBlank() {
    contextRunner
        .withPropertyValues(
            "app.backend-url=",
            // provide valid http timeouts to avoid unrelated failures
            "app.http.connect-timeout=3s",
            "app.http.response-timeout=5s",
            "app.http.read-timeout=5s",
            "app.http.write-timeout=5s")
        .run((context) -> assertThat(context).hasFailed());
  }
}
