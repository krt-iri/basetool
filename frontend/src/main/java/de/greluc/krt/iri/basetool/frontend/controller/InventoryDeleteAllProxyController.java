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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontend proxy for the admin-only "clear global inventory" action.
 *
 * <p>Forwards {@code DELETE /inventory/all} to the backend {@code DELETE /api/v1/inventory/all} via
 * the authenticated {@link WebClient} (which attaches the OAuth2 token). The backend rejects
 * non-admin callers with 403 — this controller's {@code @PreAuthorize("hasRole('ADMIN')")} adds an
 * upfront defence-in-depth check so the call never even leaves the frontend for a logistician /
 * officer caller. The {@code /inventory/all} path lives next to the existing {@code GET
 * /inventory/all} (squadron view) — Spring routes by HTTP method, so the two coexist on the same
 * URI.
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryDeleteAllProxyController {

  private final WebClient webClient;

  /**
   * Proxies the "clear global inventory" request to the backend. Admin-only.
   *
   * @return 204 No Content on success
   */
  @DeleteMapping("/all")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteAllGlobalInventory() {
    try {
      webClient.delete().uri("/api/v1/inventory/all").retrieve().toBodilessEntity().block();
      return ResponseEntity.noContent().build();
    } catch (WebClientResponseException e) {
      log.warn(
          "Delete-all-global-inventory proxy: backend returned {} — {}",
          e.getStatusCode(),
          e.getMessage());
      throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("Delete-all-global-inventory proxy: unexpected error", e);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "An unexpected error occurred while deleting all global inventory items.");
    }
  }
}
