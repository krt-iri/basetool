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

package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * SPEZIALKOMMANDO_PLAN.md §7.4 single-POST membership-delta payload. Endpoint {@code PATCH
 * /api/v1/users/{id}/memberships} accepts this record so the admin member-edit page can bundle
 * every Staffel-assignment change, every SK add / remove and every flag toggle into one
 * transactional round-trip. Per-membership-row optimistic-lock keeps concurrent admin edits from
 * silently clobbering each other.
 *
 * <p>The payload is two-part: a single {@link StaffelChange} (a user has at most one Staffel
 * membership) plus a list of {@link SpecialCommandChange}s (a user has zero-or-more SK
 * memberships). Both parts are optional — clients send only what they want to touch.
 *
 * @param staffel the desired Staffel-membership state, or {@code null} to leave the user's Staffel
 *     assignment untouched (no add, remove or flag-patch on the Staffel row).
 * @param specialCommands the list of SK changes to apply in this transaction, or {@code null} /
 *     empty when only the Staffel side is being touched.
 */
public record MembershipDeltaRequest(
    @Valid @Nullable StaffelChange staffel,
    @Valid @Nullable List<SpecialCommandChange> specialCommands) {

  /**
   * Staffel-side instruction. A user has exactly zero or one Staffel membership (enforced by the
   * V95 partial unique index), so the change is naturally a single record rather than a list.
   *
   * <p>Resolution rules:
   *
   * <ul>
   *   <li>{@link #squadronId} differs from the user's current Staffel id → reassign the user to the
   *       new Squadron (delegates to {@code UserService.updateUserSquadron} which deletes the old
   *       membership row, creates a fresh one and carries the legacy flag values). After the
   *       reassignment, any non-null flag on this record is patched onto the new row.
   *   <li>{@link #squadronId} matches the user's current Staffel id → flags only: any non-null
   *       value is patched, {@code null} leaves the field alone.
   *   <li>{@link #squadronId} is {@code null} and the user currently has a Staffel → the Staffel
   *       membership is removed (delegates to {@code UserService.updateUserSquadron} with {@code
   *       null}).
   *   <li>{@link #squadronId} is {@code null} and the user currently has no Staffel → no-op.
   * </ul>
   *
   * @param squadronId the desired Squadron id, or {@code null} to remove the Staffel membership.
   * @param isLogistician new value of the Logistician flag on the Staffel membership row, or {@code
   *     null} to leave unchanged.
   * @param isMissionManager new value of the Mission Manager flag, or {@code null} to leave
   *     unchanged.
   * @param userVersion current {@code @Version} of the {@code app_user} row; required when {@link
   *     #squadronId} changes (the delegate uses it for optimistic-lock detection on the User row);
   *     optional when only flags are being touched.
   */
  public record StaffelChange(
      @Nullable UUID squadronId,
      @Nullable Boolean isLogistician,
      @Nullable Boolean isMissionManager,
      @Nullable Long userVersion) {}

  /**
   * SK-side instruction. One entry per SK the admin wants to add, remove or patch. The {@link
   * #action} tag picks between the three branches; the remaining fields are read or ignored
   * depending on the action.
   *
   * @param orgUnitId the {@code SpecialCommand} id this entry targets; never {@code null}.
   * @param action which branch to take: {@link Action#ADD} creates a new membership row (flags
   *     default to {@code false} unless explicitly supplied); {@link Action#REMOVE} deletes the
   *     existing row; {@link Action#PATCH} updates the flag values on an existing row under
   *     optimistic-lock.
   * @param isLogistician new flag value for ADD or PATCH; ignored on REMOVE. {@code null} on PATCH
   *     means "leave unchanged"; {@code null} on ADD means "default to false".
   * @param isMissionManager new flag value for ADD or PATCH; ignored on REMOVE.
   * @param version current {@code @Version} of the membership row; required for PATCH; ignored for
   *     ADD and REMOVE.
   *     <p>{@code is_lead} is intentionally NOT part of this payload — Lead toggles stay ADMIN-only
   *     and isolated in the SK detail page workflow per plan D2, so the audit trail can attribute
   *     promotion / demotion actions cleanly to a specific admin call.
   */
  public record SpecialCommandChange(
      @NotNull UUID orgUnitId,
      @NotNull Action action,
      @Nullable Boolean isLogistician,
      @Nullable Boolean isMissionManager,
      @Nullable Long version) {

    /**
     * Action discriminator for {@link SpecialCommandChange}. Three operations the delta endpoint
     * can apply per SK membership row.
     */
    public enum Action {
      /** Create a new membership row. */
      ADD,
      /** Delete an existing membership row. */
      REMOVE,
      /** Update flags on an existing membership row under optimistic-lock. */
      PATCH
    }
  }
}
