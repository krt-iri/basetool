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

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Both transitions re-stamp AND dedupe: {@code owning_org_unit} is the eighth merge-key
 * dimension of an inventory stack, so changing it can make a row collide with an already-existing
 * identical stack of the same owner. After re-stamping, every now-identical stack is therefore
 * collapsed into one row (amounts summed) — otherwise the inventory-duplicate bug this change set
 * fixes would reappear through the back door. Private inventory ({@code personal = true}) is never
 * touched: it is owner-only regardless of org unit.
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

  /**
   * Tolerance / rounding scale shared with {@code InventoryItemService.roundAmount}: amounts are
   * stored as {@code double} at three decimals, so summing two stacks rounds back to that scale to
   * strip floating-point noise (e.g. {@code 0.1 + 0.2}).
   */
  private static final int AMOUNT_SCALE = 3;

  private final InventoryItemRepository inventoryItemRepository;

  /**
   * Reacts to a user gaining their <em>first</em> org-unit membership by promoting their
   * ownerless-personal shared inventory ({@code owning_org_unit IS NULL}) to {@code firstOrgUnit},
   * so it appears in that org unit's Lager-View. A membershipless owner can only ever hold {@code
   * NULL}-org shared rows, so no pre-existing stack of {@code firstOrgUnit} can collide; the dedupe
   * pass is a safety net.
   *
   * @param userId the owner whose shared inventory to promote; never {@code null}.
   * @param firstOrgUnit the single org unit the user just joined; never {@code null}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void onUserGainedFirstOrgUnit(@NotNull UUID userId, @NotNull OrgUnit firstOrgUnit) {
    restampAndDedupe(userId, firstOrgUnit, false);
  }

  /**
   * Reacts to a user losing their <em>last</em> org-unit membership by demoting their org-stamped
   * shared inventory back to ownerless-personal ({@code owning_org_unit = NULL}), visible only to
   * the owner. A multi-membership owner can hold same-natural-key stacks in several org units; when
   * they all become {@code NULL} those stacks collide, so the dedupe pass merges them into one row.
   *
   * @param userId the owner whose shared inventory to demote; never {@code null}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void onUserLostLastOrgUnit(@NotNull UUID userId) {
    restampAndDedupe(userId, null, true);
  }

  /**
   * Re-stamps the user's non-personal inventory and then collapses any stacks that became identical
   * as a result. When {@code demoteAllToNull} is {@code true} every org-stamped row drops to {@code
   * NULL}; otherwise every {@code NULL}-org row adopts {@code newOrgForOwnerlessRows}. The dedupe
   * groups by the natural key (owner is fixed, {@code personal} is uniformly false) and sums
   * amounts into the first row of each group, deleting the rest — relying on Hibernate dirty
   * checking for the surviving row so no explicit {@code save} is needed.
   *
   * @param userId the owner whose inventory to reconcile.
   * @param newOrgForOwnerlessRows the org unit to stamp on {@code NULL}-org rows (ignored when
   *     demoting).
   * @param demoteAllToNull {@code true} to clear every org stamp, {@code false} to promote only the
   *     {@code NULL}-org rows.
   */
  private void restampAndDedupe(
      UUID userId, @Nullable OrgUnit newOrgForOwnerlessRows, boolean demoteAllToNull) {
    List<InventoryItem> rows = inventoryItemRepository.findByUserIdAndPersonalFalse(userId);
    if (rows.isEmpty()) {
      return;
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
    if (restamped == 0) {
      return;
    }

    Map<String, InventoryItem> keepers = new LinkedHashMap<>();
    int merged = 0;
    for (InventoryItem row : rows) {
      InventoryItem keeper = keepers.putIfAbsent(naturalKey(row), row);
      if (keeper != null) {
        keeper.setAmount(round(amount(keeper) + amount(row)));
        inventoryItemRepository.delete(row);
        merged++;
      }
    }
    log.info(
        "Reconciled inventory org stamp for user {}: {} row(s) re-stamped, {} duplicate(s) merged",
        userId,
        restamped,
        merged);
  }

  /**
   * Builds the merge-identity key of a row within a single owner's non-personal inventory:
   * material, location, quality, mission, job order and owning org unit. {@code null} associations
   * render as a stable sentinel so two {@code null}s match (e.g. no mission on both sides).
   */
  private static String naturalKey(InventoryItem i) {
    return idOf(i.getMaterial() == null ? null : i.getMaterial().getId())
        + '|'
        + idOf(i.getLocation() == null ? null : i.getLocation().getId())
        + '|'
        + (i.getQuality() == null ? "-" : i.getQuality())
        + '|'
        + idOf(i.getMission() == null ? null : i.getMission().getId())
        + '|'
        + idOf(i.getJobOrder() == null ? null : i.getJobOrder().getId())
        + '|'
        + idOf(i.getOwningOrgUnit() == null ? null : i.getOwningOrgUnit().getId());
  }

  private static String idOf(@Nullable UUID id) {
    return id == null ? "-" : id.toString();
  }

  private static double amount(InventoryItem i) {
    return i.getAmount() == null ? 0.0 : i.getAmount();
  }

  private static Double round(double amount) {
    return BigDecimal.valueOf(amount).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP).doubleValue();
  }
}
