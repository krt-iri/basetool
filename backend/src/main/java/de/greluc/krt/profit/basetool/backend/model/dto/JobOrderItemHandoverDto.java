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

package de.greluc.krt.profit.basetool.backend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A persisted item-handover event with its delivered lines. Mirrors {@link JobOrderHandoverDto}
 * (the material counterpart) including the cross-staffel audit snapshot — {@code executingUser}
 * plus {@code executingSquadron} record who carried out the handover, since a logistician from one
 * squadron may fulfil another squadron's order.
 *
 * @param id the handover primary key
 * @param jobOrderId the parent order id
 * @param handoverTime when the handover occurred (UTC)
 * @param recipientHandle the recipient's handle
 * @param executingUser slim reference to the user who executed the handover ({@code null} for
 *     pre-audit rows)
 * @param executingSquadron snapshot of that user's squadron at handover time ({@code null} when
 *     unassigned)
 * @param entries the delivered item-line quantities
 * @param version optimistic-lock version
 */
public record JobOrderItemHandoverDto(
    UUID id,
    UUID jobOrderId,
    Instant handoverTime,
    String recipientHandle,
    @Nullable UserReferenceDto executingUser,
    @Nullable SquadronReferenceDto executingSquadron,
    List<JobOrderItemHandoverEntryDto> entries,
    Long version) {}
