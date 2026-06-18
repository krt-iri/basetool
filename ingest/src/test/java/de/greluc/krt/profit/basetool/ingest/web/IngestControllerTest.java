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

package de.greluc.krt.profit.basetool.ingest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.profit.basetool.ingest.model.dto.HandoffKind;
import de.greluc.krt.profit.basetool.ingest.service.BackendImportClient;
import de.greluc.krt.profit.basetool.ingest.service.HandoffStagingService;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * End-to-end web-layer test for the gateway: exercises the controller, the {@code IngestService}
 * orchestration, the security matrix and the RFC 7807 advice with the externals (backend relay,
 * Redis staging, JWT decoder) mocked (REQ-INGEST-001..004).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class IngestControllerTest {

  private static final String REFINERY_BODY =
      "{\"schemaVersion\":1,\"orders\":[{\"panelType\":\"SETUP\","
          + "\"goods\":[{\"rawMaterialName\":\"Iron\",\"inputQuantity\":1,\"refine\":true}]}]}";

  @Autowired private WebApplicationContext context;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private BackendImportClient backendImportClient;
  @MockitoBean private HandoffStagingService handoffStagingService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void shouldStageRefineryDraftAndReturnHandoff() throws Exception {
    when(backendImportClient.forwardRefineryExtract(anyString(), any(), any(), any()))
        .thenReturn("{\"goodsMatched\":1}");
    when(handoffStagingService.stage(anyString(), eq(HandoffKind.REFINERY), anyString()))
        .thenReturn("HID123");

    mockMvc
        .perform(
            post("/v1/refinery-extract")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFINERY_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.handoffId").value("HID123"))
        .andExpect(jsonPath("$.kind").value("REFINERY"))
        .andExpect(
            jsonPath("$.frontendUrl")
                .value(org.hamcrest.Matchers.containsString("handoff=HID123")));
  }

  @Test
  void shouldRejectUnauthenticatedCaller() throws Exception {
    mockMvc
        .perform(
            post("/v1/refinery-extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFINERY_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReject400OnInvalidExtract() throws Exception {
    mockMvc
        .perform(
            post("/v1/refinery-extract")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\":1,\"orders\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void shouldStageBlueprintPreview() throws Exception {
    when(backendImportClient.forwardBlueprintPreview(anyString(), any(), any(), any()))
        .thenReturn("{\"total\":2}");
    when(handoffStagingService.stage(anyString(), eq(HandoffKind.BLUEPRINT), anyString()))
        .thenReturn("BP1");

    mockMvc
        .perform(
            post("/v1/blueprint-preview")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blueprints\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("BLUEPRINT"));
  }

  @Test
  void shouldReject400WhenBlueprintBodyIsNotAnObject() throws Exception {
    mockMvc
        .perform(
            post("/v1/blueprint-preview")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void shouldRelayBackend4xxVerbatim() throws Exception {
    when(backendImportClient.forwardRefineryExtract(anyString(), any(), any(), any()))
        .thenThrow(
            WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                null));

    mockMvc
        .perform(
            post("/v1/refinery-extract")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFINERY_BODY))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn502WhenBackendUnreachable() throws Exception {
    when(backendImportClient.forwardRefineryExtract(anyString(), any(), any(), any()))
        .thenThrow(
            new WebClientRequestException(
                new RuntimeException("connection refused"),
                HttpMethod.POST,
                URI.create("https://backend:11261/api/v1/refinery-orders/import-extract"),
                HttpHeaders.EMPTY));

    mockMvc
        .perform(
            post("/v1/refinery-extract")
                .with(jwt().jwt(j -> j.subject("user-1").tokenValue("tok")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFINERY_BODY))
        .andExpect(status().isBadGateway());
  }
}
