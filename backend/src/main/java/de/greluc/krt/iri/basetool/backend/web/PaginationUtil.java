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

package de.greluc.krt.iri.basetool.backend.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Static helpers that translate query-string pagination parameters into a Spring Data {@link
 * Pageable} while enforcing the project's invariants: every list endpoint sorts against a fixed
 * whitelist (no user-supplied JPA paths), every page request gets {@code id} appended as a
 * tiebreaker so pages remain stable, and the {@code size} parameter is clamped so a single request
 * cannot pin the database with an unbounded fetch.
 */
public final class PaginationUtil {

  private PaginationUtil() {}

  /**
   * Builds a {@link Pageable} from raw query parameters.
   *
   * <p>{@code page} defaults to 0 and is clamped to a non-negative value. {@code size} defaults to
   * 50 and is clamped to {@code [1, 100000]}. {@code sort} is parsed as a semicolon-separated list
   * of {@code field,asc|desc} tokens; unknown fields cause an {@link IllegalArgumentException}
   * which the global error handler maps to a 400. If {@code id} is whitelisted but not already in
   * the sort, it is appended as a tiebreaker so two equal primary-sort rows always come back in a
   * deterministic order across pages.
   *
   * @param pageParam zero-based page index, may be {@code null}
   * @param sizeParam page size, may be {@code null}
   * @param sortParam raw {@code sort} query parameter, may be {@code null} or blank
   * @param allowedSortFields whitelist of sortable field names (must include {@code
   *     defaultSortField})
   * @param defaultSortField fallback field used when {@code sortParam} is null/blank
   * @return a {@link Pageable} ready to hand to a repository
   * @throws IllegalArgumentException when {@code sortParam} contains a field not in {@code
   *     allowedSortFields}
   */
  public static Pageable createPageRequest(
      Integer pageParam,
      Integer sizeParam,
      String sortParam,
      Set<String> allowedSortFields,
      String defaultSortField) {
    int page = pageParam == null || pageParam < 0 ? 0 : pageParam;
    int size = sizeParam == null || sizeParam <= 0 ? 50 : Math.min(sizeParam, 100000);

    Sort sort = resolveSort(sortParam, allowedSortFields, defaultSortField);
    // Ensure stability: always add a secondary sort by id if not already included
    if (!containsProperty(sort, "id") && allowedSortFields.contains("id")) {
      sort = sort.and(Sort.by("id"));
    }
    return PageRequest.of(page, size, sort);
  }

  private static boolean containsProperty(Sort sort, String property) {
    for (Sort.Order order : sort) {
      if (order.getProperty().equalsIgnoreCase(property)) {
        return true;
      }
    }
    return false;
  }

  private static Sort resolveSort(String sortParam, Set<String> allowed, String defaultField) {
    if (sortParam == null || sortParam.isBlank()) {
      return Sort.by(defaultField).ascending();
    }
    List<Sort.Order> orders = new ArrayList<>();
    // Support multiple fields separated by semicolon or repeated commas: field,asc;other,desc
    String[] parts = sortParam.split("[;]");
    for (String part : parts) {
      String[] tokens = part.split(",");
      String field = tokens[0].trim();
      if (!allowed.contains(field)) {
        throw new IllegalArgumentException("Unsupported sort field: " + field);
      }
      Sort.Direction dir = Sort.Direction.ASC;
      if (tokens.length > 1) {
        String d = tokens[1].trim();
        if (d.equalsIgnoreCase("desc")) {
          dir = Sort.Direction.DESC;
        }
      }
      orders.add(new Sort.Order(dir, field));
    }
    if (orders.isEmpty()) {
      return Sort.by(defaultField).ascending();
    }
    return Sort.by(orders);
  }

  /**
   * Renders a {@link Sort} back into the {@code field,direction} string form that the controllers
   * echo into the {@code PageResponse.sort} field, so the client receives the same syntax it sent
   * in and can reuse it verbatim on the next page request.
   *
   * @param sort sort object as built by {@link #createPageRequest}
   * @return list of {@code field,asc|desc} tokens in declaration order
   */
  public static List<String> toSortStrings(Sort sort) {
    return sort.stream()
        .map(o -> o.getProperty() + "," + o.getDirection().name().toLowerCase())
        .collect(Collectors.toList());
  }
}
