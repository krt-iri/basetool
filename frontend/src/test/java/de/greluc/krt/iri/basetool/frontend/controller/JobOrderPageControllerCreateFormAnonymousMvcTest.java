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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Regression test for the "anonymous guest cannot import scmdb shopping list" bug: an
 * unauthenticated caller hitting GET {@code /orders/create} must load the job-order materials
 * catalog through the public WebClient ({@code isPublic=true}), not the OAuth2-bearer-relaying
 * authenticated one.
 *
 * <p>Symptom before the fix: the page rendered with an empty {@code material-options-template}
 * (because the authenticated WebClient had no bearer token for an anonymous caller), so the
 * client-side scmdb parser found zero matches for legitimate material names and surfaced "Folgende
 * Materialien wurden nicht im System gefunden: Agricium, ...". The fix routes {@code
 * fetchMaterials()} through the public client, mirroring {@code fetchSquadrons()} and the
 * order-creation POST.
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class JobOrderPageControllerCreateFormAnonymousMvcTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @WithAnonymousUser
  void viewCreateForm_AsAnonymousGuest_ShouldFetchMaterialsThroughPublicWebClient()
      throws Exception {
    when(backendApiClient.getCached(anyString(), any(ParameterizedTypeReference.class), eq(true)))
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/orders/create"))
        .andExpect(status().isOk())
        .andExpect(view().name("orders-create"));

    verify(backendApiClient)
        .getCached(
            eq("/api/v1/materials/job-order"), any(ParameterizedTypeReference.class), eq(true));
    verify(backendApiClient, never())
        .getCached(eq("/api/v1/materials/job-order"), any(ParameterizedTypeReference.class));
  }
}
