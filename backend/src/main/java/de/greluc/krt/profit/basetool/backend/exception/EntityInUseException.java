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

package de.greluc.krt.profit.basetool.backend.exception;

/**
 * Thrown when an entity cannot be deleted because it is still referenced by other entities.
 *
 * <p>Mapped to HTTP {@code 409 Conflict} by {@link
 * de.greluc.krt.profit.basetool.backend.exception.GlobalExceptionHandler}'s generic {@code
 * AppException} dispatch handler with the stable error code {@code ENTITY_IN_USE}. Every accessor
 * is inherited unchanged from {@link AppException} — it delegates to {@link
 * AppExceptionKind#ENTITY_IN_USE}, the fixed identity passed to the superclass constructor.
 */
public final class EntityInUseException extends AppException {

  /**
   * Creates an {@code EntityInUseException} with a description of the blocking reference.
   *
   * @param message human-readable explanation of which referencing entities still exist
   */
  public EntityInUseException(String message) {
    super(AppExceptionKind.ENTITY_IN_USE, message);
  }
}
