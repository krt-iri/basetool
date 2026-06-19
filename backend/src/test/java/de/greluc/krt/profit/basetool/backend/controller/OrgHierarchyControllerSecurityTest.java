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

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

  /**
   * Every remaining {@code /api/v1/org-hierarchy/**} read and write endpoint, each paired with a
   * <em>valid</em> body / path so the only thing that can reject the request is the {@code
   * hasRole('ADMIN')} gate (not bean validation or a missing path variable). Keeps the surface from
   * silently losing its ADMIN gate as endpoints are added.
   *
   * @return one request builder per endpoint.
   */
  static Stream<Arguments> adminOnlyEndpoints() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    return Stream.of(
        arguments(
            Named.of(
                "listOrganisationsleitung",
                get("/api/v1/org-hierarchy/organisationsleitung")
                    .accept(MediaType.APPLICATION_JSON))),
        arguments(
            Named.of(
                "createOrganisationsleitung",
                post("/api/v1/org-hierarchy/organisationsleitung")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Leitung\",\"shorthand\":\"OL\"}"))),
        arguments(
            Named.of(
                "setParent",
                patch("/api/v1/org-hierarchy/org-units/" + id + "/parent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0}"))),
        arguments(
            Named.of(
                "addBereichLeader",
                post("/api/v1/org-hierarchy/bereiche/" + id + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"" + id + "\",\"role\":\"LEITER\"}"))),
        arguments(
            Named.of(
                "removeBereichLeader",
                delete("/api/v1/org-hierarchy/bereiche/" + id + "/members/" + id))),
        arguments(
            Named.of(
                "addOlMember",
                post("/api/v1/org-hierarchy/organisationsleitung/" + id + "/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"" + id + "\"}"))),
        arguments(
            Named.of(
                "removeOlMember",
                delete("/api/v1/org-hierarchy/organisationsleitung/" + id + "/members/" + id))));
  }

  @ParameterizedTest(name = "{0} is forbidden for an OFFICER")
  @MethodSource("adminOnlyEndpoints")
  void endpoint_forbiddenForOfficer(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc
        .perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
        .andExpect(status().isForbidden());
  }
}
