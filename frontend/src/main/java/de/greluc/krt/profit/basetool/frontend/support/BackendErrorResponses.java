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

package de.greluc.krt.profit.basetool.frontend.support;

import de.greluc.krt.profit.basetool.frontend.service.BackendServiceException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Shared relay of a backend {@link BackendServiceException} back to the browser as {@code
 * application/problem+json}, replacing the byte-for-byte-identical {@code propagateBackendError}
 * method that the in-place-AJAX page controllers each carried privately.
 *
 * <p>The emitted body preserves the backend's stable {@code code} (e.g. {@code OPTIMISTIC_LOCK})
 * and status plus, when present, the problem {@code detail} and the {@code correlationId}, so the
 * shared client-side {@code krtFetch} branches on the conflict semantics exactly as it did before.
 */
public final class BackendErrorResponses {

  private BackendErrorResponses() {}

  /**
   * Builds a {@code problem+json} {@link ResponseEntity} mirroring the backend failure: the backend
   * HTTP status, the stable problem {@code code}, and the optional {@code detail}/{@code
   * correlationId} (each omitted when blank/absent).
   *
   * @param e the backend failure to relay
   * @return a {@link ResponseEntity} carrying the backend status and an {@code
   *     application/problem+json} body
   */
  @NotNull
  public static ResponseEntity<Object> propagateBackendError(@NotNull BackendServiceException e) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", e.getStatusCode());
    body.put("code", e.getProblemCode());
    if (e.getProblemDetail() != null && !e.getProblemDetail().isBlank()) {
      body.put("detail", e.getProblemDetail());
    }
    if (e.getCorrelationId() != null && !e.getCorrelationId().isBlank()) {
      body.put("correlationId", e.getCorrelationId());
    }
    return ResponseEntity.status(e.getStatusCode())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }
}
