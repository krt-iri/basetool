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

import de.greluc.krt.profit.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST proxy for the user-autocomplete used by participant/owner pickers.
 *
 * <p>Browser-side JS calls land on {@code /users/search} (no {@code /api/} prefix because Spring
 * Security treats this path as authenticated-only-by-default); the controller forwards to the
 * backend {@code /api/v1/users/search} with the bearer token attached by {@link BackendApiClient}.
 * The page size is hardcoded at 1000 and sorted by username — autocomplete lists are short, so a
 * single page covers any realistic squadron.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProxyController {

  private final BackendApiClient backendApiClient;

  /**
   * Forwards the autocomplete query to the backend search endpoint and unwraps the page payload
   * into a flat list. Empty list on backend failure or missing content — the autocomplete renders
   * an empty result rather than surfacing the error.
   *
   * @param query free-text query to forward to the backend
   * @return matching user records (raw JSON maps), never {@code null}
   */
  @GetMapping("/search")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String, Object>> searchUsers(@RequestParam String query) {
    // L-1: UriComponentsBuilder so the user-supplied query is properly query-param-encoded
    // and cannot inject extra parameters via crafted `&`-separators.
    String uri =
        org.springframework.web.util.UriComponentsBuilder.fromPath("/api/v1/users/search")
            .queryParam("query", query)
            .queryParam("size", 1000)
            .queryParam("sort", "username,asc")
            .toUriString();
    PageResponse<Map<String, Object>> response =
        backendApiClient.get(
            uri, new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});
    return response != null && response.content() != null ? response.content() : List.of();
  }
}
