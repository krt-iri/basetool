package de.greluc.krt.iri.basetool.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

class BackendPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties({RateLimitProperties.class, KeycloakSyncProperties.class})
    static class TestConfig {
        @Bean
        LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }

    @Test
    void shouldFail_WhenRateLimitCapacityIsZero() {
        contextRunner
                .withPropertyValues(
                        "app.rate-limit.capacity=0",
                        "app.rate-limit.refillTokens=1",
                        "app.rate-limit.refillPeriod=1m"
                )
                .run((context) -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFail_WhenKeycloakAdminUrlInvalid() {
        contextRunner
                .withPropertyValues(
                        "app.keycloak.sync.enabled=true",
                        "app.keycloak.sync.admin-url=htp://not-a-url",
                        "app.keycloak.sync.realm=iri",
                        "app.keycloak.sync.client-id=backend-service",
                        "app.keycloak.sync.client-secret=secret"
                )
                .run((context) -> assertThat(context).hasFailed());
    }
}
