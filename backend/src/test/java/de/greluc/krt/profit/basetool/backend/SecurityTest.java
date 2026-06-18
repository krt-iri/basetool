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

package de.greluc.krt.profit.basetool.backend;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.profit.basetool.backend.filter.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private RateLimitingFilter rateLimitingFilter;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void testCorsHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/missions")
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().exists("Access-Control-Allow-Origin"));
  }

  @Test
  void testCorsHeaders_ForbiddenOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/missions")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void testSecurityHeaders() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().exists("Content-Security-Policy"));
  }

  /**
   * Pins the hardened Content-Security-Policy for the JSON-only backend. The backend serves no HTML
   * (Swagger UI was removed), so the policy locks down to {@code default-src 'none'}. This test
   * fails loudly if a future change re-introduces the Swagger-era relaxations ({@code
   * 'unsafe-inline'} on {@code style-src}, {@code data:} img/font sources) or otherwise loosens the
   * lockdown — those would silently re-open a (would-be) XSS surface.
   *
   * @throws Exception if the MockMvc request fails
   */
  @Test
  void contentSecurityPolicyIsLockedDownForJsonOnlyBackend() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action"
                        + " 'none'"));
  }

  @Test
  void testRateLimiting() throws Exception {
    // First request should pass
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToMissions() throws Exception {
    mockMvc.perform(get("/api/v1/missions")).andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessToMissions() throws Exception {
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", java.util.UUID.randomUUID().toString())
            .claim("preferred_username", "testuser")
            .build();

    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToLocations() throws Exception {
    mockMvc.perform(get("/api/v1/locations")).andExpect(status().isOk());
  }

  @Test
  void testAnonymousAccessToJobTypes() throws Exception {
    mockMvc.perform(get("/api/v1/job-types")).andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithInvalidSub() throws Exception {
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "not-a-uuid")
            .claim("preferred_username", "testuser")
            .build();

    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithNullSub() throws Exception {
    // We create a JWT without a sub claim
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "testuser")
            .build();

    // This should now succeed and not log ERROR (only log WARN)
    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }

  @Test
  void testAuthenticatedAccessWithBothNullSubAndUsername() throws Exception {
    // We create a JWT without sub and without preferred_username, but with some other claim to
    // satisfy Jwt.Builder
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("foo", "bar")
            .build();

    // This should log ERROR but still return 200 for permitAll
    mockMvc
        .perform(
            get("/api/v1/missions")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt)))
        .andExpect(status().isOk());
  }
}
