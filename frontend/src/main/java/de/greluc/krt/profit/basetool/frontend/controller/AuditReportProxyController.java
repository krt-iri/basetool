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

package de.greluc.krt.profit.basetool.frontend.controller;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Frontend proxy for the unified audit-log PDF exports (REQ-AUDIT-001, ADR-0037). One seam for all
 * five tabs: {@code BANK} routes to the bank admin export, the four generic areas to {@code
 * /api/v1/audit/{domain}/export}. Streams the backend's PDF bytes back to the browser via the
 * authenticated {@link WebClient} (OAuth2 token attached automatically) and forwards the caller's
 * IANA time zone so the documents render local timestamps. Authorization (ADMIN) is decided by the
 * backend gates; this seam only requires authentication.
 */
@RestController
@RequestMapping("/api/proxy/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditReportProxyController {

  private final WebClient webClient;

  /**
   * Proxies one area's audit-log PDF export for a caller-chosen period.
   *
   * @param domain the area tab ({@code BANK} or a generic {@code AuditDomain} name)
   * @param from period start (ISO-8601 instant, forwarded verbatim)
   * @param to period end (ISO-8601 instant, forwarded verbatim)
   * @param userTimeZone the caller's IANA time zone; optional
   * @return the PDF with attachment headers
   */
  @GetMapping("/{domain}/export")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<byte[]> downloadAuditLog(
      @PathVariable @NotNull String domain,
      @RequestParam @NotNull String from,
      @RequestParam @NotNull String to,
      @RequestHeader(value = "X-User-Time-Zone", required = false) String userTimeZone) {
    String backendBase =
        "BANK".equals(domain)
            ? "/api/v1/bank/admin/audit/export"
            : "/api/v1/audit/" + domain + "/export";
    String uri =
        UriComponentsBuilder.fromPath(backendBase)
            .queryParam("from", from)
            .queryParam("to", to)
            .toUriString();
    String filename = "audit-" + domain.toLowerCase(Locale.ROOT) + ".pdf";
    return fetchAttachment(uri, userTimeZone, filename, MediaType.APPLICATION_PDF);
  }

  /**
   * Proxies one area's audit-log JSON export for a caller-chosen period (REQ-AUDIT-003). JSON
   * carries UTC instants verbatim, so no time-zone header is forwarded.
   *
   * @param domain the area tab ({@code BANK} or a generic {@code AuditDomain} name)
   * @param from period start (ISO-8601 instant, forwarded verbatim)
   * @param to period end (ISO-8601 instant, forwarded verbatim)
   * @return the JSON with attachment headers
   */
  @GetMapping("/{domain}/export.json")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<byte[]> downloadAuditLogJson(
      @PathVariable @NotNull String domain,
      @RequestParam @NotNull String from,
      @RequestParam @NotNull String to) {
    String backendBase =
        "BANK".equals(domain)
            ? "/api/v1/bank/admin/audit/export.json"
            : "/api/v1/audit/" + domain + "/export.json";
    String uri =
        UriComponentsBuilder.fromPath(backendBase)
            .queryParam("from", from)
            .queryParam("to", to)
            .toUriString();
    String filename = "audit-" + domain.toLowerCase(Locale.ROOT) + ".json";
    return fetchAttachment(uri, null, filename, MediaType.APPLICATION_JSON);
  }

  /**
   * Fetches one backend export document and re-wraps it with attachment headers; backend errors
   * propagate with their original status so the page can surface 400/403 distinctly.
   *
   * @param uri the backend URI incl. query
   * @param userTimeZone the zone header to forward; may be {@code null}
   * @param filename the download filename
   * @param mediaType the response content type (PDF or JSON)
   * @return the proxied attachment response
   */
  private ResponseEntity<byte[]> fetchAttachment(
      @NotNull String uri,
      String userTimeZone,
      @NotNull String filename,
      @NotNull MediaType mediaType) {
    try {
      byte[] pdf =
          webClient
              .get()
              .uri(uri)
              .headers(
                  h -> {
                    if (userTimeZone != null && !userTimeZone.isBlank()) {
                      h.set("X-User-Time-Zone", userTimeZone);
                    }
                  })
              .retrieve()
              .bodyToMono(byte[].class)
              .block();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(mediaType);
      headers.setContentDispositionFormData("attachment", filename);
      return ResponseEntity.ok().headers(headers).body(pdf);
    } catch (WebClientResponseException e) {
      log.warn("Audit report proxy: backend returned {} for {}", e.getStatusCode(), uri);
      throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("Audit report proxy: unexpected error for {}", uri, e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "An unexpected error occurred while generating the audit log report.");
    }
  }
}
