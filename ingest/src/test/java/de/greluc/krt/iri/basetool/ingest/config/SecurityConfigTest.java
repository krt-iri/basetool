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

package de.greluc.krt.iri.basetool.ingest.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for the opt-in audience validator (REQ-INGEST-002/-008): a token passes only when its
 * {@code aud} intersects the configured expected set.
 */
class SecurityConfigTest {

  private static final List<String> EXPECTED = List.of("basetool-backend");

  private static Jwt jwtWithAudience(List<String> audience) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user-1")
        .audience(audience)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
  }

  @Test
  void shouldAcceptTokenWhoseAudienceContainsAnExpectedValue() {
    // Given
    OAuth2TokenValidator<Jwt> validator = SecurityConfig.audienceValidator(EXPECTED);
    Jwt jwt = jwtWithAudience(List.of("other", "basetool-backend"));

    // When
    OAuth2TokenValidatorResult result = validator.validate(jwt);

    // Then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldRejectTokenWithoutAnyExpectedAudience() {
    // Given
    OAuth2TokenValidator<Jwt> validator = SecurityConfig.audienceValidator(EXPECTED);
    Jwt jwt = jwtWithAudience(List.of("basetool-frontend"));

    // When
    OAuth2TokenValidatorResult result = validator.validate(jwt);

    // Then
    assertThat(result.hasErrors()).isTrue();
  }
}
