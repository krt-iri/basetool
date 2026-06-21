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

package de.greluc.krt.profit.basetool.frontend.model.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying User payload.
 *
 * <p>{@code discordLinked} is a privacy-safe, read-only indicator mirrored from the backend: {@code
 * true} when a Discord account is federated to the user's Basetool account, {@code false}
 * otherwise, and {@code null} on peer/guest-redacted projections that never expose it. The raw
 * Discord id is never carried — only this boolean. The admin member-management page renders it as
 * the Discord column (REQ-SEC-019).
 */
public record UserDto(
    UUID id,
    String username,
    String displayName,
    String effectiveName,
    String email,
    Integer rank,
    String description,
    Set<String> roles,
    Set<String> permissions,
    UUID lastReadAnnouncementId,
    Boolean isLogistician,
    Boolean isMissionManager,
    Boolean inKeycloak,
    @Nullable SquadronReferenceDto squadron,
    Long version,
    @Nullable LocalDate joinDate,
    @Nullable Boolean discordLinked) {}
