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

package de.greluc.krt.profit.basetool.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end verification of the RFC-7807 hardening for Spring Security's filter-level rejections
 * (REQ-API-004 / REQ-SEC): an unauthenticated request to a protected endpoint returns {@code 401
 * application/problem+json} with code {@code UNAUTHENTICATED}, and an authenticated caller lacking
 * the required role returns {@code 403 application/problem+json} with code {@code ACCESS_DENIED} —
 * both carrying a {@code correlationId}, instead of Spring's default bare 401/403. Exercised
 * against the ADMIN-gated {@code /api/v1/audit/**} matcher, whose deny verdict is reached at the
 * authorization filter before any controller.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityProblemResponseIntegrationTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void unauthenticatedRequest_returns401ProblemJsonWithCorrelationId() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit/logs"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void authenticatedButUnauthorized_returns403ProblemJsonWithCorrelationId() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_KRT_MEMBER"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void unauthenticatedRequest_echoesCorrelationIdResponseHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit/logs"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Correlation-Id"));
  }
}
