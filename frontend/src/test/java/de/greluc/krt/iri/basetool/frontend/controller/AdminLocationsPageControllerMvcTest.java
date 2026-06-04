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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
 * Regression for the Thymeleaf 3.1 JS-inline truncation bug on {@code /admin/locations}.
 *
 * <p>Pre-fix, the template called {@code /*[[${locations.![name]}]]*&#47;} inside a {@code
 * th:inline="javascript"} script, which truncated the rest of the script body. The page-local
 * {@code filterTable} function lived after that expression and therefore was never defined — the
 * filter input above the locations table did nothing. The fix replaces the inline expression with a
 * sibling {@code <datalist id="locationNames-data">}; this test pins that {@code filterTable}
 * survives in the rendered HTML and the response ends with the closing {@code </html>} tag.
 */
@SpringBootTest
class AdminLocationsPageControllerMvcTest {

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

  /**
   * Asserts {@code function filterTable(...)} (which is defined AFTER the datalist in the script)
   * appears in the rendered HTML — proof that the Thymeleaf truncation does not strike again.
   */
  @Test
  @WithMockUser(roles = "ADMIN")
  void listData_ShouldRenderFilterTableFunction_AfterDatalist() throws Exception {
    LocationDto location =
        new LocationDto(UUID.randomUUID(), "ARC-L1", "Arc-Corp Lagrange 1", false, false, 0L);
    PageResponse<LocationDto> page =
        new PageResponse<>(List.of(location), 0, 1000, 1, 1, Collections.emptyList());

    when(backendApiClient.get(
            eq("/api/v1/locations?size=1000&sort=name,asc&includeHidden=true"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(page);

    mockMvc
        .perform(get("/admin/locations"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/locations"))
        .andExpect(content().string(containsString("id=\"locationNames-data\"")))
        .andExpect(content().string(containsString("value=\"ARC-L1\"")))
        .andExpect(content().string(containsString("function filterTable(tableId, query)")))
        .andExpect(content().string(containsString("</html>")));
  }
}
