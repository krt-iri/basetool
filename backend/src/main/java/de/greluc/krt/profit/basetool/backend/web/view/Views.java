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

package de.greluc.krt.profit.basetool.backend.web.view;

/**
 * Jackson {@code @JsonView} marker classes used to drop sensitive fields from public responses.
 *
 * <p>Fields tagged with {@link Internal} are only rendered for authenticated callers; fields tagged
 * with {@link Public} are rendered for everyone. The {@code Internal extends Public} relation lets
 * a single annotation on an internal field implicitly include all public fields when the internal
 * view is selected.
 */
public class Views {
  /** Marker for fields that may appear in unauthenticated (guest) responses. */
  public static class Public {}

  /**
   * Marker for fields that must only appear in authenticated responses. Extends {@link Public} so
   * selecting the internal view automatically includes the public surface.
   */
  public static class Internal extends Public {}
}
