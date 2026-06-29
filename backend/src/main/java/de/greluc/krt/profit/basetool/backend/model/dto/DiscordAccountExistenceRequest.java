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

/**
 * Candidate identity of an incoming Discord first-broker-login, sent by the Keycloak SPI to the
 * internal account-existence precheck (REQ-SEC-022). Every field is optional — the SPI sends
 * whatever it has, and a {@code null}/blank field is simply not matched.
 *
 * <p>The two name fields ({@code username}, {@code serverNickname}) are matched,
 * case-insensitively, against existing accounts' login username <em>and</em> in-app display name;
 * {@code email} is matched against existing accounts' e-mail. None of these values is ever logged
 * (REQ-OBS).
 *
 * @param username the brokered Discord username (global handle)
 * @param email the brokered Discord e-mail address, if the user shared it
 * @param serverNickname the user's per-guild server nickname in the das-kartell Discord, if any
 */
public record DiscordAccountExistenceRequest(
    String username, String email, String serverNickname) {}
