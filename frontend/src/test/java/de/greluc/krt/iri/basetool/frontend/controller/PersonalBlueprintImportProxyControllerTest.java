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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintImportApplyRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintImportPreviewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintImportResolutionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintImportResultDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BlueprintImportStatus;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link PersonalBlueprintImportProxyController}. {@link MockWebServer} stands in
 * for the backend so the real WebClient multipart chain on the preview path is exercised; the JSON
 * apply path mocks {@link BackendApiClient}.
 */
class PersonalBlueprintImportProxyControllerTest {

  private MockWebServer server;
  private BackendApiClient backendApiClient;
  private PersonalBlueprintImportProxyController controller;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    backendApiClient = mock(BackendApiClient.class);
    controller =
        new PersonalBlueprintImportProxyController(
            webClient,
            backendApiClient,
            mock(de.greluc.krt.iri.basetool.frontend.service.IngestHandoffService.class));
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      server.shutdown();
    } catch (Exception ignored) {
      // already shut down in connection-failure tests
    }
  }

  @Test
  void preview_proxiesMultipartAndParsesPreview() throws Exception {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"total\":2,\"matched\":1,\"matchedByAlias\":0,\"suggested\":1,\"unmatched\":0,"
                    + "\"alreadyOwned\":0,\"entries\":["
                    + "{\"externalName\":\"Arclight Pistol\",\"status\":\"MATCHED\","
                    + "\"productKey\":\"arclight pistol\",\"productName\":\"Arclight Pistol\","
                    + "\"outputItemId\":null,\"suggestedAcquiredAt\":null,\"suggestions\":[]},"
                    + "{\"externalName\":\"Calico Legs Tacticl\",\"status\":\"SUGGESTED\","
                    + "\"productKey\":null,\"productName\":null,\"outputItemId\":null,"
                    + "\"suggestedAcquiredAt\":null,\"suggestions\":[{\"productKey\":"
                    + "\"calico legs tactical\",\"productName\":\"Calico Legs Tactical\","
                    + "\"score\":0.9}]}]}"));

    MultipartFile file =
        new MockMultipartFile(
            "file",
            "scmdb.json",
            "application/json",
            "{\"blueprints\":[]}".getBytes(StandardCharsets.UTF_8));

    BlueprintImportPreviewDto preview = controller.preview(file);

    assertNotNull(preview);
    assertEquals(2, preview.total());
    assertEquals(1, preview.matched());
    assertEquals(1, preview.suggested());
    assertEquals(2, preview.entries().size());
    assertEquals(BlueprintImportStatus.MATCHED, preview.entries().get(0).status());
    assertEquals(
        "calico legs tactical", preview.entries().get(1).suggestions().get(0).productKey());

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req);
    assertEquals("POST", req.getMethod());
    assertEquals("/api/v1/personal-blueprints/import/preview", req.getPath());
    assertTrue(req.getHeader("Content-Type").startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
    assertTrue(req.getBody().readUtf8().contains("filename=\"scmdb.json\""));
  }

  @Test
  void preview_onBackend400_propagatesAsBadRequest() {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("Invalid JSON"));

    MultipartFile file =
        new MockMultipartFile(
            "file", "broken.json", "application/json", "garbage".getBytes(StandardCharsets.UTF_8));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> controller.preview(file));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void apply_wrapsResolutionsInRequestAndRelays() {
    when(backendApiClient.post(
            eq("/api/v1/personal-blueprints/import/apply"),
            any(BlueprintImportApplyRequest.class),
            eq(BlueprintImportResultDto.class)))
        .thenReturn(new BlueprintImportResultDto(2, 1, 0, 0, 0));

    BlueprintImportResultDto result =
        controller.apply(
            List.of(
                new BlueprintImportResolutionDto(
                    "Arclight Pistol", "arclight pistol", null, "imported")));

    assertEquals(2, result.added());
    assertEquals(1, result.aliasesLearned());
  }

  @Test
  void apply_onBackendError_wrapsAs500() {
    when(backendApiClient.post(any(), any(), eq(BlueprintImportResultDto.class)))
        .thenThrow(new RuntimeException("boom"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.apply(List.of(new BlueprintImportResolutionDto("x", "k", null, null))));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
  }
}
