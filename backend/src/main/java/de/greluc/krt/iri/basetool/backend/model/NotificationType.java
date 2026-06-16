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

package de.greluc.krt.iri.basetool.backend.model;

/**
 * Machine identifier of a {@link Notification}'s kind.
 *
 * <p>The constant name is persisted verbatim (via {@code @Enumerated(STRING)}) and is the key the
 * frontend resolves to an i18n message under {@code notifications.type.*}. The set is intentionally
 * open-ended: a new producer adds a constant here (and the matching i18n keys + an optional default
 * rule) without a schema migration, because the {@code notification.type} column carries no CHECK
 * constraint. The data-driven recipient rules and the rendered text stay configurable; only the
 * stable type token is code.
 */
public enum NotificationType {

  /**
   * A new job order ("Auftrag") was created. Recipients are resolved per the seeded default rule
   * (officers of the responsible squadron / leads of the responsible special command, plus the
   * logisticians of that unit and the global admins; the creating actor is excluded).
   */
  JOB_ORDER_CREATED
}
