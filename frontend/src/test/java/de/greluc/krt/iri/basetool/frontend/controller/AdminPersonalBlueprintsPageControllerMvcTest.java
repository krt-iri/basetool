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

package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * MVC-level test for {@link AdminPersonalBlueprintsPageController}: the admin Blueprints page
 * renders for an ADMIN and is forbidden for a non-admin (the {@code hasRole('ADMIN')} gate).
 */
@SpringBootTest
class AdminPersonalBlueprintsPageControllerMvcTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void view_rendersForAdmin_withUserPicker() throws Exception {
    PageResponse<UserDto> users = new PageResponse<>(List.of(), 0, 1000, 0, 0, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(users);

    mockMvc
        .perform(get("/admin/personal-blueprints"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/personal-blueprints"))
        .andExpect(model().attributeExists("users"))
        .andExpect(model().attributeExists("blueprints"))
        .andExpect(model().attribute("adminMode", Boolean.TRUE));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void view_rendersIdPlaceholderToken_inPerRowEndpoints() throws Exception {
    PageResponse<UserDto> empty = new PageResponse<>(List.of(), 0, 1000, 0, 0, List.of());
    when(backendApiClient.get(anyString(), any(ParameterizedTypeReference.class)))
        .thenReturn(empty);

    // With a user selected, the inline-JS endpoint map renders. The per-row updateNote/remove
    // URLs must keep the literal ID_PLACEHOLDER token verbatim; a double-underscore __ID__ would
    // be eaten by Thymeleaf preprocessing and render "ID", 400ing on UUID parse.
    mockMvc
        .perform(
            get("/admin/personal-blueprints")
                .param("userSub", "00000000-0000-0000-0000-000000000009"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("ID_PLACEHOLDER")));
  }

  @Test
  @WithMockUser(roles = "SQUADRON_MEMBER")
  void view_forbiddenForNonAdmin() throws Exception {
    mockMvc.perform(get("/admin/personal-blueprints")).andExpect(status().isForbidden());
  }
}
