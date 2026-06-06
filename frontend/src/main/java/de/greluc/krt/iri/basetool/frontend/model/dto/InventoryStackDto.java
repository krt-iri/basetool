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

package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one display stack: append-only inventory rows that share a stock identity
 * (owner, location, quality, job-order / mission association, personal flag, owning org-unit pool)
 * collapsed into a single row for the Lager view. The aggregate figures describe the collapsed row;
 * {@code entries} is the list of underlying rows, oldest-first, on which every per-entry action
 * operates.
 *
 * @param user the owning user shared by every entry
 * @param location the storage location shared by every entry
 * @param quality the quality grade shared by every entry
 * @param jobOrderId the linked job-order id, or {@code null}
 * @param jobOrderDisplayId the linked job-order's display id, or {@code null}
 * @param missionId the linked mission id, or {@code null}
 * @param missionName the linked mission's name, or {@code null}
 * @param personal whether the stack holds private stock
 * @param owningSquadron the owning org-unit pool, or {@code null}
 * @param totalAmount the summed quantity across all entries
 * @param averageQuality the amount-weighted mean quality
 * @param maxQuality the highest quality among the entries
 * @param entryCount the number of underlying entries
 * @param entries the underlying individual rows, oldest-first
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
