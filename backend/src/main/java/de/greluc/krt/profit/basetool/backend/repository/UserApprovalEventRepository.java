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

import de.greluc.krt.profit.basetool.backend.model.UserApprovalEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence for the append-only {@link UserApprovalEvent} approval-decision audit. */
@Repository
public interface UserApprovalEventRepository extends JpaRepository<UserApprovalEvent, UUID> {

  /**
   * Bulk-deletes every audit row whose subject is the given user. Called by the user-delete flow so
   * hard-deleting a since-removed (no-longer-in-Keycloak) account is not blocked by the NOT-NULL
   * {@code user_id} foreign key of this V173 audit table, which carries no {@code ON DELETE} clause
   * (Postgres {@code NO ACTION}). The row's whole purpose is "this user's registration was
   * decided", so it is removed together with the user rather than orphaned.
   *
   * @param userId the subject user whose audit rows to delete; never {@code null}.
   */
  @Modifying
  @Query("DELETE FROM UserApprovalEvent e WHERE e.userId = :userId")
  void deleteByUserId(@Param("userId") UUID userId);

  /**
   * Bulk-nulls the {@code decided_by_id} attribution on every audit row decided by the given admin.
   * Called by the user-delete flow when the deleted account had itself decided registrations, so
   * the still-valid audit of those <em>other</em> users survives but drops its now-gone decider
   * reference — matching the entity's nullable "system action" contract — instead of blocking the
   * delete on the {@code decided_by_id} foreign key (also no {@code ON DELETE} clause).
   *
   * @param adminId the deciding admin being deleted; never {@code null}.
   */
  @Modifying
  @Query("UPDATE UserApprovalEvent e SET e.decidedById = null WHERE e.decidedById = :adminId")
  void clearDecidedBy(@Param("adminId") UUID adminId);
}
