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

package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * A read-time grouping of inventory entries that share the same stock identity — what the Lager
 * used to merge into a single physical row but now keeps as separate {@code InventoryItem} rows.
 * The stack key is the inventory natural key minus the material (which is the enclosing {@link
 * GroupedInventoryDto} group): owner ({@code user}), {@code location}, {@code quality}, the
 * optional job-order / mission association, the {@code personal} flag and the {@code
 * owningSquadron} owner pool. The aggregate figures ({@code totalAmount}, {@code averageQuality},
 * {@code maxQuality}, {@code entryCount}) are computed across {@code entries} for the collapsed
 * display row; {@code entries} is the full list of the underlying rows, ordered oldest-first, on
 * which every per-entry action (book-out, transfer, note, delivered, delete) operates by id +
 * version.
 *
 * @param user the owning user shared by every entry in the stack
 * @param location the storage location shared by every entry
 * @param quality the quality grade shared by every entry
 * @param jobOrderId the linked job-order id, or {@code null} when unassigned
 * @param jobOrderDisplayId the linked job-order's human display id, or {@code null}
 * @param missionId the linked mission id, or {@code null} when unassigned
 * @param missionName the linked mission's name, or {@code null}
 * @param personal whether the stack holds private (owner-only) stock
 * @param owningSquadron the owning org-unit pool shared by every entry, or {@code null} for an
 *     ownerless-personal stack
 * @param totalAmount the summed quantity across all entries (SCU or pieces)
 * @param averageQuality the amount-weighted mean quality across all entries
 * @param maxQuality the highest quality value among the entries
 * @param entryCount the number of underlying entries collapsed into this stack
 * @param entries the underlying individual rows, ordered oldest-first by creation instant
 */
public record InventoryStackDto(
    UserReferenceDto user,
    LocationReferenceDto location,
    Integer quality,
    UUID jobOrderId,
    Integer jobOrderDisplayId,
    UUID missionId,
    String missionName,
    Boolean personal,
    SquadronReferenceDto owningSquadron,
    Double totalAmount,
    Double averageQuality,
    Integer maxQuality,
    Integer entryCount,
    List<InventoryItemDto> entries) {}
