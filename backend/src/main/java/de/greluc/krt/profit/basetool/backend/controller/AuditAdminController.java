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

import de.greluc.krt.profit.basetool.backend.model.AuditDomain;
import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.dto.AuditEventDto;
import de.greluc.krt.profit.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.backend.service.AuditReportService;
import de.greluc.krt.profit.basetool.backend.service.AuditService;
import de.greluc.krt.profit.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only activity audit surface (REQ-AUDIT-001, ADR-0037): the paged, filterable per-area audit
 * viewer and the per-area period exports (PDF + JSON). Double-gated — the {@code /api/v1/audit/**}
 * URL matcher requires {@code ADMIN} before these method gates even run.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditAdminController {

  private static final Set<String> AUDIT_SORT_FIELDS = Set.of("occurredAt", "id");

  private final AuditService auditService;
  private final AuditReportService auditReportService;

  /**
   * Pages over one area's filtered audit log (REQ-AUDIT-001), newest first by default.
   *
   * @param domain the area to read (path: the selected tab)
   * @param from period start (inclusive, ISO instant), or absent
   * @param to period end (inclusive, ISO instant), or absent
   * @param actorUserId filter on the acting user, or absent
   * @param eventType filter on the event type, or absent
   * @param page zero-based page index
   * @param size page size
   * @param sort whitelisted sort spec
   * @return one page of audit events for that area
   */
  @Operation(summary = "Read an activity audit log (admin; paged, filterable)")
  @GetMapping("/{domain}")
  @Transactional(readOnly = true)
  public PageResponse<AuditEventDto> getAuditLog(
      @PathVariable AuditDomain domain,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) UUID actorUserId,
      @RequestParam(required = false) AuditEventType eventType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    String effectiveSort = sort == null || sort.isBlank() ? "occurredAt,desc" : sort;
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, effectiveSort, AUDIT_SORT_FIELDS, "occurredAt");
    Page<AuditEventDto> result =
        auditService.getEvents(domain, from, to, actorUserId, eventType, pageable);
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        PaginationUtil.toSortStrings(result.getSort()));
  }

  /**
   * Renders one area's audit log as a KRT-design PDF for a chosen period (REQ-AUDIT-001). The
   * optional {@code X-User-Time-Zone} header localizes the document timestamps; an invalid IANA
   * zone is silently dropped. Each export is itself audit-logged.
   *
   * @param domain the area to export
   * @param from period start (inclusive, ISO instant)
   * @param to period end (inclusive, ISO instant); must not be before {@code from}
   * @param userTimeZone IANA zone (e.g. {@code Europe/Berlin}); optional
   * @return PDF body with {@code application/pdf} and attachment Content-Disposition
   */
  @Operation(summary = "Export an activity audit log as a PDF for a period (admin)")
  @GetMapping("/{domain}/export")
  public ResponseEntity<byte[]> exportAuditLog(
      @PathVariable AuditDomain domain,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestHeader(value = "X-User-Time-Zone", required = false) String userTimeZone) {
    byte[] pdf = auditReportService.generateAuditLogPdf(domain, from, to, parseZone(userTimeZone));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData(
        "attachment", "audit-" + domain.name().toLowerCase(java.util.Locale.ROOT) + ".pdf");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * Exports one area's audit log as a downloadable JSON document for a chosen period
   * (REQ-AUDIT-003). The body is the period's events as DTOs; an attachment Content-Disposition
   * makes the browser save it as a file. Each export is itself audit-logged.
   *
   * @param domain the area to export
   * @param from period start (inclusive, ISO instant)
   * @param to period end (inclusive, ISO instant); must not be before {@code from}
   * @return the period's audit events as JSON with attachment headers
   */
  @Operation(summary = "Export an activity audit log as JSON for a period (admin)")
  @GetMapping("/{domain}/export.json")
  public ResponseEntity<java.util.List<AuditEventDto>> exportAuditLogJson(
      @PathVariable AuditDomain domain,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    java.util.List<AuditEventDto> events =
        auditReportService.generateAuditLogJson(domain, from, to);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setContentDispositionFormData(
        "attachment", "audit-" + domain.name().toLowerCase(java.util.Locale.ROOT) + ".json");
    return ResponseEntity.ok().headers(headers).body(events);
  }

  /**
   * Parses an IANA time-zone id, tolerating a blank or invalid value by falling back to {@code
   * null} (the report service then renders in UTC).
   *
   * @param zoneId the raw header value, or {@code null}
   * @return the parsed zone, or {@code null}
   */
  private static ZoneId parseZone(String zoneId) {
    if (zoneId == null || zoneId.isBlank()) {
      return null;
    }
    try {
      return ZoneId.of(zoneId.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }
}
