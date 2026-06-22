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

package de.greluc.krt.profit.basetool.backend.model;

/**
 * The functional area an {@link AuditEvent} belongs to (REQ-AUDIT-001). The shared {@code
 * audit_event} table is one physical store; this discriminator keeps the four logs
 * <em>logically</em> separate so the admin viewer can switch between them and each can be exported
 * on its own (ADR-0037). The bank audit trail is deliberately <strong>not</strong> a value here —
 * it keeps its own {@code bank_audit_event} table and is surfaced on the same page as a fifth tab.
 */
public enum AuditDomain {

  /** Squadron warehouse stock — the {@code InventoryItem} aggregate (Lagerverwaltung). */
  INVENTORY,

  /** Job orders — the {@code JobOrder} aggregate and its claims/handovers (Auftragsverwaltung). */
  JOB_ORDER,

  /** Refinery orders and refinery reference data (Raffinerieverwaltung). */
  REFINERY,

  /** Per-user personal stash — the {@code PersonalInventoryItem} aggregate (Mein Inventar). */
  PERSONAL_INVENTORY,

  /**
   * Missions — the {@code Mission} aggregate and its participants/units/crew/finance (Missionen).
   */
  MISSION,

  /** Operations — the {@code Operation} aggregate and its payout toggles (Operationen). */
  OPERATION
}
