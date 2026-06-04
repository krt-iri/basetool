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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontend proxy for the third-party ship-export import endpoint. Accepts CCU Game Fleetview,
 * HangarXPLOR Shiplist, Fleetyards and StarJump FleetViewer JSON payloads; the backend auto-detects
 * the format from the payload shape.
 *
 * <p>Receives the multipart file from the browser, forwards it to the backend {@code POST
 * /api/v1/hangar/import/ships} via the authenticated {@link WebClient} (which automatically
 * attaches the OAuth2 token), and returns the backend response as-is to the browser.
 *
 * <p>The {@code /hangar/import/fleetview} path is retained as a deprecated alias that forwards to
 * the same backend endpoint, so any cached browser script or bookmark continues to work until the
 * sunset date communicated by the backend's {@code Sunset} response header.
 */
@RestController
@RequestMapping("/hangar/import")
@RequiredArgsConstructor
@Slf4j
public class HangarImportProxyController {

  private final WebClient webClient;

  /**
   * Proxies a ship-export JSON file upload from the browser to the backend import endpoint.
   *
   * @param file the uploaded JSON file (Fleetview, HangarXPLOR Shiplist, Fleetyards or StarJump
   *     FleetViewer)
   * @return the backend response (a {@code FleetviewImportResponseDto}) as a raw JSON map
   */
  @PostMapping(value = "/ships", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<?, ?>> importShips(@RequestParam("file") @NotNull MultipartFile file) {
    return forwardImport(file, "/api/v1/hangar/import/ships");
  }

  /**
   * Legacy path retained for browser scripts and bookmarks that still target the old Fleetview-only
   * endpoint. Forwards to the same backend endpoint as {@link #importShips(MultipartFile)}.
   *
   * @param file the uploaded JSON file
   * @return the backend response (a {@code FleetviewImportResponseDto}) as a raw JSON map
   * @deprecated use {@code POST /hangar/import/ships} — this path keeps working until the backend's
   *     sunset date but new code should target the format-neutral path directly.
   */
  @PostMapping(value = "/fleetview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Deprecated(since = "2026-05-14", forRemoval = true)
  public ResponseEntity<Map<?, ?>> importFleetview(
      @RequestParam("file") @NotNull MultipartFile file) {
    return forwardImport(file, "/api/v1/hangar/import/fleetview");
  }

  /**
   * Shared multipart-forwarding plumbing for both the canonical and the legacy path. Handles the
   * file body re-wrap, error translation from {@link WebClientResponseException} to a matching
   * {@link ResponseStatusException}, and the unexpected-error fallback.
   *
   * @param file uploaded multipart file
   * @param backendPath relative path on the backend (without host) to forward to
   * @return the backend response unchanged
   */
  private @NotNull ResponseEntity<Map<?, ?>> forwardImport(
      @NotNull MultipartFile file, @NotNull String backendPath) {
    try {
      byte[] bytes = file.getBytes();
      String originalFilename =
          file.getOriginalFilename() != null ? file.getOriginalFilename() : "shiplist.json";

      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      builder
          .part(
              "file",
              new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                  return originalFilename;
                }
              })
          .contentType(MediaType.APPLICATION_OCTET_STREAM);

      Map<?, ?> result =
          webClient
              .post()
              .uri(backendPath)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(BodyInserters.fromMultipartData(builder.build()))
              .retrieve()
              .bodyToMono(Map.class)
              .block();

      return ResponseEntity.ok(result);
    } catch (WebClientResponseException e) {
      log.warn("Hangar import proxy: backend returned {} — {}", e.getStatusCode(), e.getMessage());
      throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("Hangar import proxy: unexpected error", e);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "An unexpected error occurred during import.");
    }
  }
}
