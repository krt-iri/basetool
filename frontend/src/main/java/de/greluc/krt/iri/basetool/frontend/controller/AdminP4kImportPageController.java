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

import de.greluc.krt.iri.basetool.frontend.model.dto.P4kImportResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring MVC controller backing the {@code /admin/p4k-import} page (#326, P4K catalog import): an
 * admin uploads a JSON catalog extracted from the Star Citizen game files, previews the resolution
 * (dry run, nothing written), then optionally applies it — optionally seeding brand-new game rows.
 *
 * <p>Both upload actions proxy the multipart {@code file} part verbatim to the backend admin import
 * surface ({@code /api/v1/admin/import/p4k/preview} and {@code .../apply}) via the authenticated
 * {@link WebClient}, which injects the OAuth2 bearer token automatically. The {@link
 * P4kImportResultDto} is relayed unchanged to the page JS, which renders the per-type count table.
 *
 * <p>Admin-only — class-level {@code @PreAuthorize("hasRole('ADMIN')")} matches the backend gate.
 * Mirrors {@link AdminPersonalBlueprintsPageController}'s multipart-proxy pattern.
 */
@Controller
@RequestMapping("/admin/p4k-import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminP4kImportPageController {

  private final WebClient webClient;

  /**
   * Renders the P4K import page. The page itself is static chrome plus the upload controls; the
   * proxy endpoint URLs are exposed to the inlined JS so it can POST the picked file.
   *
   * @param model Thymeleaf model
   * @return the {@code admin/p4k-import} view name
   */
  @GetMapping
  public String view(Model model) {
    model.addAttribute("previewUrl", "/admin/p4k-import/preview");
    model.addAttribute("applyUrl", "/admin/p4k-import/apply");
    return "admin/p4k-import";
  }

  /**
   * Previews a P4K catalog import (dry run — the backend writes nothing). The uploaded JSON is
   * forwarded as multipart {@code file} to the backend preview endpoint via the authenticated
   * WebClient. Backend parse/validation failures (4xx) propagate as the same status.
   *
   * @param file the uploaded P4K catalog JSON
   * @return the import result preview (with {@code dryRun = true})
   */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public P4kImportResultDto preview(@RequestParam("file") @NotNull MultipartFile file) {
    return forward(file, "/api/v1/admin/import/p4k/preview");
  }

  /**
   * Applies a reviewed P4K catalog import. The uploaded JSON is forwarded as multipart {@code file}
   * to the backend apply endpoint via the authenticated WebClient, with the {@code seedNew} opt-in
   * relayed as a query parameter. Backend parse/validation failures (4xx) propagate as the same
   * status.
   *
   * @param file the uploaded P4K catalog JSON
   * @param seedNew whether to insert brand-new game rows that have no existing match
   * @return the applied import result (with {@code dryRun = false})
   */
  @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public P4kImportResultDto apply(
      @RequestParam("file") @NotNull MultipartFile file,
      @RequestParam(defaultValue = "false") boolean seedNew) {
    return forward(file, "/api/v1/admin/import/p4k/apply?seedNew=" + seedNew);
  }

  /**
   * Shared multipart-proxy path: rebuilds the upload as a single {@code file} part and POSTs it to
   * {@code targetUri} on the authenticated backend WebClient. Maps a backend {@link
   * WebClientResponseException} to a {@link ResponseStatusException} of the same status so the page
   * JS sees a non-2xx and toasts the error; any other failure collapses to a 500.
   *
   * @param file the uploaded P4K catalog JSON
   * @param targetUri the backend URI (path + any query string) to forward to
   * @return the decoded import result
   */
  private P4kImportResultDto forward(MultipartFile file, String targetUri) {
    try {
      byte[] bytes = file.getBytes();
      String filename =
          file.getOriginalFilename() != null ? file.getOriginalFilename() : "p4k-catalog.json";
      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      builder
          .part(
              "file",
              new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                  return filename;
                }
              })
          .contentType(MediaType.APPLICATION_OCTET_STREAM);

      return webClient
          .post()
          .uri(targetUri)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToMono(P4kImportResultDto.class)
          .block();
    } catch (WebClientResponseException e) {
      log.warn(
          "P4K import proxy ({}): backend {} — {}", targetUri, e.getStatusCode(), e.getMessage());
      throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("P4K import proxy ({}): unexpected error", targetUri, e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred during the P4K import.");
    }
  }
}
