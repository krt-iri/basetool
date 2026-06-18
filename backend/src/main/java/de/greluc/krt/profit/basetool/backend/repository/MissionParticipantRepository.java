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

package de.greluc.krt.profit.basetool.backend.repository;

import de.greluc.krt.profit.basetool.backend.model.MissionParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Participant. */
public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code MissionIdAndUserId}. */
  Optional<MissionParticipant> findByMissionIdAndUserId(UUID missionId, UUID userId);

  /**
   * Returns {@code true} iff at least one participant is affiliated with the org unit (Staffel or
   * Spezialkommando) of the given id, traversing the {@code orgUnits} many-to-many. Replaces the
   * former {@code existsBySquadronId} after the participant affiliation moved from the single
   * {@code squadron_id} FK to the {@code mission_participant_org_unit} join.
   *
   * @param orgUnitId the org-unit id to check for any participant affiliation.
   * @return {@code true} iff at least one participant references the org unit.
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT COUNT(mp) > 0 FROM MissionParticipant mp JOIN mp.orgUnits ou WHERE ou.id ="
          + " :orgUnitId")
  boolean existsByOrgUnitId(
      @org.springframework.data.repository.query.Param("orgUnitId") UUID orgUnitId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * DesiredMissionJobTypeId}.
   */
  boolean existsByDesiredMissionJobTypeId(UUID jobTypeId);

  /** Derived Spring-Data query - returns entities matching {@code DesiredMissionJobTypeId}. */
  List<MissionParticipant> findByDesiredMissionJobTypeId(UUID jobTypeId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * PlannedMissionJobTypeId}.
   */
  boolean existsByPlannedMissionJobTypeId(UUID jobTypeId);

  /** Derived Spring-Data query - returns entities matching {@code PlannedMissionJobTypeId}. */
  List<MissionParticipant> findByPlannedMissionJobTypeId(UUID jobTypeId);

  /**
   * Bulk-clears the {@code user} reference on every mission participant linked to the given user;
   * used by the user-delete flow so mission history (guest name, status) survives but the personal
   * link is removed.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE MissionParticipant mp SET mp.user = null WHERE mp.user.id = :userId")
  void unlinkUser(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}
