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

package de.greluc.krt.iri.basetool.backend.event;

import java.util.UUID;

/**
 * Published by {@code UserService} the first time a local {@code app_user} row is created for a
 * Keycloak user (first login or the scheduled directory sync). Consumed after commit to provision
 * per-user defaults — currently the default-blueprint grant (REQ-INV-016).
 *
 * <p>Carries only the immutable user id (never a managed entity) so the listener can run safely in
 * a fresh transaction after the originating one commits.
 *
 * @param userId the newly provisioned user's id (the Keycloak {@code sub} as a UUID)
 */
public record UserProvisionedEvent(UUID userId) {}
