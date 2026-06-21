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

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying User payload.
 *
 * <p>{@code discordLinked} is a privacy-safe, read-only indicator derived from {@code
 * app_user.discord_user_id} (see {@code UserMapper#toDto}): {@code true} when the user has a
 * Discord account federated to their Basetool account, {@code false} otherwise. The raw Discord id
 * (a snowflake) is never carried in any DTO — only the boolean fact of the link, surfaced as the
 * Discord column on the admin member-management page (REQ-SEC-019). Peer/guest redaction shapes
 * leave it {@code null} so the link status never reaches non-admins.
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
    Boolean discordLinked) {}
