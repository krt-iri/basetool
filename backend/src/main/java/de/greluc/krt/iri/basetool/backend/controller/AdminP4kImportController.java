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

package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.P4kImportResultDto;
import de.greluc.krt.iri.basetool.backend.service.P4kImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-only endpoint that drives the KRT P4K Reader catalog import. An administrator uploads the
 * single JSON catalog the external KRT P4K Reader extracts from the game's {@code Data/Game2.dcb}
 * and the {@link P4kImportService} enriches / reconciles the existing master-data rows against it
 * and can optionally seed brand-new rows for genuinely-new game data UEX / SC-Wiki do not carry
 * yet. Two steps mirror the other admin import surfaces:
 *
 * <ul>
 *   <li>{@code POST /preview} computes every reconciliation action (including how many rows seeding
 *       would create) without writing.
 *   <li>{@code POST /apply} applies the same actions — enriching existing rows and, when {@code
 *       seedNew} is set, seeding new ones — and records its findings in the P4K sync-report.
 * </ul>
 *
 * <p>The {@code ADMIN} role is enforced at this boundary; the service stays free of the security
 * context.
 */
@RestController
@RequestMapping("/api/v1/admin/import/p4k")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Admin – P4K Import",
    description =
        "Administrator endpoints for importing the KRT P4K Reader catalog (Game2.dcb master data).")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class AdminP4kImportController {

  private final P4kImportService importService;

  /**
   * Previews a P4K catalog import: parses the upload and reports how each record would reconcile,
   * without persisting anything.
   *
   * @param file the uploaded P4K catalog JSON
   * @return the per-type action summary ({@code dryRun = true})
   */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Preview a P4K catalog import (no writes).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Preview computed."),
    @ApiResponse(responseCode = "400", description = "File empty or not valid P4K catalog JSON."),
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator.")
  })
  public P4kImportResultDto preview(@RequestParam("file") @NotNull MultipartFile file) {
    return importService.previewImport(file);
  }

  /**
   * Applies a P4K catalog import: enriches / reconciles the matching master-data rows, optionally
   * seeds brand-new rows for unmatched player-facing records, and records the backfill / conflict /
   * seed findings in the P4K sync-report.
   *
   * @param file the uploaded P4K catalog JSON
   * @param seedNew {@code true} to insert new {@code source = P4K} rows for unmatched records that
   *     pass the real-record filter; {@code false} (default) to enrich existing rows only
   * @return the per-type action summary ({@code dryRun = false}) with the stamped run id
   */
  @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary =
          "Apply a P4K catalog import (enriches existing rows; optionally seeds new ones via"
              + " seedNew).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Import applied; see the summary."),
    @ApiResponse(responseCode = "400", description = "File empty or not valid P4K catalog JSON."),
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator.")
  })
  public P4kImportResultDto apply(
      @RequestParam("file") @NotNull MultipartFile file,
      @RequestParam(value = "seedNew", defaultValue = "false") boolean seedNew) {
    return importService.applyImport(file, seedNew);
  }
}
