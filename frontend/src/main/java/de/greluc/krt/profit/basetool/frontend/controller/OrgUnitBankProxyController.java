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

import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin AJAX proxy for the org-unit officer/lead bank actions ({@code /api/proxy/org-units/bank/**},
 * epic #666 F2). Browser-side JS posts here with the CSRF header; the proxy forwards the raw JSON
 * to the corresponding {@code /api/v1/org-units/bank/**} backend endpoint with the OAuth2 bearer
 * attached by {@link BackendApiClient}. Authentication is enforced at this seam; the real
 * authorization (oversight scope, request ownership) lives in the backend. Kept separate from the
 * bank-staff {@code BankProxyController} so the two audiences never share a proxy path.
 */
@RestController
@RequestMapping("/api/proxy/org-units/bank")
@RequiredArgsConstructor
public class OrgUnitBankProxyController {

  private final BackendApiClient backendApiClient;

  /**
   * Forwards a new booking request raised by an officer/lead against their overseen org unit's
   * account (REQ-BANK-022). Out-of-scope (403) and closed-account (409) surface inline.
   *
   * @param body the raw create payload (orgUnitId + type + amount + optional note)
   * @return the created pending request
   */
  @PostMapping("/requests")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createRequest(@RequestBody @NotNull Map<String, Object> body) {
    return postMap("/api/v1/org-units/bank/requests", body);
  }

  /**
   * Forwards the cancellation of the caller's own pending booking request (REQ-BANK-022).
   *
   * @param id the request to cancel
   * @param body the lifecycle payload (echoed version)
   * @return the cancelled request
   */
  @PostMapping("/requests/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  public Map<String, Object> cancelRequest(
      @PathVariable @NotNull UUID id, @RequestBody @NotNull Map<String, Object> body) {
    return postMap("/api/v1/org-units/bank/requests/" + id + "/cancel", body);
  }

  /**
   * POST helper returning the backend's JSON body as a raw map (empty map for a bodyless 2xx),
   * keeping the browser contract uniform with {@code BankProxyController}.
   *
   * @param uri the backend endpoint
   * @param body the forwarded payload
   * @return the backend response body
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> postMap(@NotNull String uri, @NotNull Map<String, Object> body) {
    Map<String, Object> response = backendApiClient.post(uri, body, Map.class);
    return response == null ? Map.of() : response;
  }
}
