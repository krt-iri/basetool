/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BackendPropertiesValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
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
            "app.rate-limit.refillPeriod=1m")
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
            "app.keycloak.sync.client-secret=secret")
        .run((context) -> assertThat(context).hasFailed());
  }
}
