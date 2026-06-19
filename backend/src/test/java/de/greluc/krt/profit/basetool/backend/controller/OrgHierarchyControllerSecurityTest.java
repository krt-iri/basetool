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

package de.greluc.krt.profit.basetool.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies the {@code /api/v1/org-hierarchy/**} surface (epic #692) is ADMIN-only: defining the
 * Bereich/OL hierarchy is org-unit lifecycle administration. An OFFICER is forbidden on both the
 * read and the write endpoints; an ADMIN is admitted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrgHierarchyControllerSecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void listBereiche_forbiddenForOfficer() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/org-hierarchy/bereiche")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void listBereiche_allowedForAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/org-hierarchy/bereiche")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void createBereich_forbiddenForOfficer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/org-hierarchy/bereiche")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Profit\",\"shorthand\":\"PRF\"}"))
        .andExpect(status().isForbidden());
  }
}
