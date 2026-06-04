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

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same-origin proxy for the admin-only squadron toggle endpoints. Currently exposes only the
 * per-squadron promotion-feature flag flip; the admin-settings page sends a PATCH here when the
 * admin clicks the "Beförderung aktiv" checkbox so the browser never has to know the backend
 * hostname and the CSRF-protected session is reused via {@link BackendApiClient}.
 *
 * <p>Endpoints carry their own {@code ADMIN}-role gate at the Spring Security layer; the backend
 * re-checks the role, so this proxy is defence-in-depth and not the sole guard.
 */
@RestController
@RequestMapping("/api/proxy/squadrons")
@RequiredArgsConstructor
public class SquadronAdminProxyController {

  private final BackendApiClient backendApiClient;

  /**
   * Forwards a "set squadron promotion-enabled flag" request to the backend. Returns 204 No Content
   * on success so the AJAX caller does not have to parse the squadron payload — it already knows
   * the new state from the checkbox event.
   *
   * @param id squadron primary key
   * @param body request payload {@code { "enabled": true|false }}
   * @return 204 No Content on success
   */
  @PatchMapping("/{id}/promotion-enabled")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> setPromotionEnabled(
      @PathVariable @NotNull UUID id, @RequestBody @NotNull Map<String, Object> body) {
    backendApiClient.patch("/api/v1/squadrons/" + id + "/promotion-enabled", body, Void.class);
    return ResponseEntity.noContent().build();
  }

  /**
   * Forwards a "set squadron profit-eligible flag" request to the backend. Returns 204 No Content
   * on success so the AJAX caller does not have to parse the squadron payload — it already knows
   * the new state from the checkbox event.
   *
   * @param id squadron primary key
   * @param body request payload {@code { "eligible": true|false }}
   * @return 204 No Content on success
   */
  @PatchMapping("/{id}/profit-eligible")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> setProfitEligible(
      @PathVariable @NotNull UUID id, @RequestBody @NotNull Map<String, Object> body) {
    backendApiClient.patch("/api/v1/squadrons/" + id + "/profit-eligible", body, Void.class);
    return ResponseEntity.noContent().build();
  }
}
