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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.OrgRelativeRole;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/**
 * Resolves a notification rule selector's abstract recipient description into concrete user {@code
 * sub}s.
 *
 * <p>Officer-ness is a global Keycloak role mirrored into {@code user_roles}, so "officers of
 * squadron X" is the role {@code OFFICER} intersected with membership of X; Lead / Logistician /
 * Mission-Manager are per-membership flags read straight off the org unit. Because the role mirror
 * is refreshed by {@code UserSyncTask} (≤5 min), a freshly-promoted officer who has not logged in
 * yet is invisible here until the next sync — accepted eventual consistency (REQ-NOTIF-008).
 */
@Service
@RequiredArgsConstructor
public class RecipientResolutionService {

  /** Stable code of the global Officer role in {@code role.code}. */
  static final String ROLE_OFFICER = "OFFICER";

  private final UserRepository userRepository;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;

  /**
   * Resolves every holder of a global role by its stable code.
   *
   * @param roleCode the role code (e.g. {@code ADMIN})
   * @return the matching user subs; never {@code null}
   */
  @NotNull
  public Set<UUID> resolveByRole(@NotNull String roleCode) {
    return userRepository.findUserIdsByRoleCode(roleCode);
  }

  /**
   * Resolves a role evaluated relative to a specific org unit.
   *
   * @param role the org-relative role
   * @param orgUnitId the context org unit
   * @return the matching user subs; never {@code null}
   */
  @NotNull
  public Set<UUID> resolveOrgRelative(@NotNull OrgRelativeRole role, @NotNull UUID orgUnitId) {
    return switch (role) {
      case OFFICER ->
          userRepository.findUserIdsByRoleCodeAndOrgUnitMembership(ROLE_OFFICER, orgUnitId);
      case LEAD -> orgUnitMembershipRepository.findLeadUserIdsByOrgUnit(orgUnitId);
      case LOGISTICIAN -> orgUnitMembershipRepository.findLogisticianUserIdsByOrgUnit(orgUnitId);
      case MISSION_MANAGER ->
          orgUnitMembershipRepository.findMissionManagerUserIdsByOrgUnit(orgUnitId);
    };
  }
}
