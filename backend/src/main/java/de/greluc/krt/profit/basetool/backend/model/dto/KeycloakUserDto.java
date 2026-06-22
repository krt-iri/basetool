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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Keycloak User payload for the scheduled admin-API user sync.
 *
 * <p>{@code id}, {@code username}, {@code email} and {@code enabled} are deserialized directly from
 * the Keycloak Admin {@code GET /users} response; {@code roles} is joined in afterwards from the
 * per-user realm-role mapping. {@code discordUserId} is likewise enriched out-of-band from the
 * per-user {@code GET /users/{id}/federated-identity} call (the bulk {@code /users} listing does
 * not carry it): it is the Discord snowflake of the {@code discord} federated link, or {@code null}
 * when the user has no Discord link or the federated-identity lookup could not be performed.
 * Persisting it onto {@code app_user.discord_user_id} is what lets the member-management Discord
 * indicator (REQ-SEC-019) recognise accounts linked to Discord <em>after</em> creation, not only
 * those that registered via Discord (REQ-DATA-006).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserDto(
    UUID id,
    String username,
    String email,
    Boolean enabled,
    Set<String> roles,
    @Nullable String discordUserId) {}
