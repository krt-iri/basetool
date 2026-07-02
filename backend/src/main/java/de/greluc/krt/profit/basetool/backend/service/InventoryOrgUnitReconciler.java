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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.InventoryItem;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.support.AuditDetails;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps an inventory entry's {@code owning_org_unit} stamp consistent with its owner's org-unit
 * membership. It reacts to the two boundary transitions the product owner chose to auto-sync:
 *
 * <ul>
 *   <li><b>First membership gained</b> (membershipless → member of exactly one org unit): the
 *       owner's ownerless-personal shared rows ({@code personal = false}, {@code owning_org_unit IS
 *       NULL}) adopt that org unit so they surface in its Lager-View.
 *   <li><b>Last membership lost</b> (member → membershipless): the owner's org-stamped shared rows
 *       fall back to ownerless-personal ({@code owning_org_unit = NULL}), owner-visible only.
 * </ul>
 *
 * <p>Both transitions <em>only re-stamp</em> {@code owning_org_unit}; they never merge rows. Two
 * rows that become identical after a re-stamp (for example stock from two org units both demoted to
 * {@code NULL}) stay separate — inventory is append-only and the Lager view collapses same-identity
 * rows for display (group-on-read, see {@code InventoryItemService.aggregateInventoryItems}).
 * Private inventory ({@code personal = true}) is never touched: it is owner-only regardless of org
 * unit.
 *
 * <p>Every method requires an already-open transaction ({@link Propagation#MANDATORY}) because it
 * runs as part of the membership-mutation transaction in {@link OrgUnitMembershipService}, so the
 * re-stamp commits atomically with the membership change and a rollback there also rolls back the
 * inventory re-stamp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryOrgUnitReconciler {

  private final InventoryItemRepository inventoryItemRepository;
  private final AuditService auditService;

  /**
   * Reacts to a user gaining their <em>first</em> org-unit membership by promoting their
   * ownerless-personal shared inventory ({@code owning_org_unit IS NULL}) to {@code firstOrgUnit},
   * so it appears in that org unit's Lager-View.
   *
   * @param userId the owner whose shared inventory to promote; never {@code null}.
   * @param firstOrgUnit the single org unit the user just joined; never {@code null}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void onUserGainedFirstOrgUnit(@NotNull UUID userId, @NotNull OrgUnit firstOrgUnit) {
    int restamped = restamp(userId, firstOrgUnit, false);
    if (restamped > 0) {
      auditService.record(
          AuditEventType.INVENTORY_ORG_RESTAMPED,
          null,
          null,
          userId,
          AuditDetails.of("trigger", "GAINED_FIRST")
              .with("orgUnit", firstOrgUnit.getId())
              .with("rows", restamped));
    }
  }

  /**
   * Reacts to a user losing their <em>last</em> org-unit membership by demoting their org-stamped
   * shared inventory back to ownerless-personal ({@code owning_org_unit = NULL}), visible only to
   * the owner. Rows that become identical after the demotion stay separate; the Lager view groups
   * them for display (group-on-read).
   *
   * @param userId the owner whose shared inventory to demote; never {@code null}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void onUserLostLastOrgUnit(@NotNull UUID userId) {
    int restamped = restamp(userId, null, true);
    if (restamped > 0) {
      auditService.record(
          AuditEventType.INVENTORY_ORG_RESTAMPED,
          null,
          null,
          userId,
          AuditDetails.of("trigger", "LOST_LAST").with("orgUnit", "NULL").with("rows", restamped));
    }
  }

  /**
   * Re-stamps the user's non-personal inventory in place: when {@code demoteAllToNull} is {@code
   * true} every org-stamped row drops to {@code NULL}; otherwise every {@code NULL}-org row adopts
   * {@code newOrgForOwnerlessRows}. Rows are never merged — append-only inventory keeps every row
   * and the Lager view collapses same-identity rows for display. Relies on Hibernate dirty checking
   * for the changed rows, so no explicit {@code save} is needed.
   *
   * @param userId the owner whose inventory to reconcile.
   * @param newOrgForOwnerlessRows the org unit to stamp on {@code NULL}-org rows (ignored when
   *     demoting).
   * @param demoteAllToNull {@code true} to clear every org stamp, {@code false} to promote only the
   *     {@code NULL}-org rows.
   * @return the number of rows actually re-stamped (0 when nothing changed)
   */
  private int restamp(
      UUID userId, @Nullable OrgUnit newOrgForOwnerlessRows, boolean demoteAllToNull) {
    List<InventoryItem> rows = inventoryItemRepository.findByUserIdAndPersonalFalse(userId);
    if (rows.isEmpty()) {
      return 0;
    }

    int restamped = 0;
    for (InventoryItem row : rows) {
      if (demoteAllToNull) {
        if (row.getOwningOrgUnit() != null) {
          row.setOwningOrgUnit(null);
          restamped++;
        }
      } else if (row.getOwningOrgUnit() == null) {
        row.setOwningOrgUnit(newOrgForOwnerlessRows);
        restamped++;
      }
    }
    if (restamped > 0) {
      log.info(
          "Reconciled inventory org stamp for user {}: {} row(s) re-stamped", userId, restamped);
    }
    return restamped;
  }
}
