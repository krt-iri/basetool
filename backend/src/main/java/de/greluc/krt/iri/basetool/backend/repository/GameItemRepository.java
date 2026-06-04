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

package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.GameItem;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link GameItem}. The sync services use {@link
 * #findByExternalUuid(UUID)} as the primary fast-path and {@link #findByUexItemId(Integer)} as the
 * secondary path; the soft-delete sweep at the end of a successful sync run uses {@link
 * #markUexDeletedExcept(Collection, Instant)} to flag rows UEX no longer returns.
 */
@Repository
public interface GameItemRepository extends JpaRepository<GameItem, UUID> {

  /**
   * Resolution-chain step 1 (R2): match an inbound DTO row to an existing local row via the shared
   * in-game asset UUID. {@code external_uuid} is the joint key with the {@code UNIQUE} constraint
   * enforcing the §3.6 identity invariant.
   *
   * @param externalUuid in-game asset UUID
   * @return matching {@link GameItem} if present
   */
  Optional<GameItem> findByExternalUuid(UUID externalUuid);

  /**
   * Resolution-chain step 2 (R2 UEX side): match by UEX's integer item id. Used when the UEX
   * payload carries no UUID (~30% of rows) but the row was already created on a previous run.
   *
   * @param uexItemId UEX's integer item id
   * @return matching {@link GameItem} if present
   */
  Optional<GameItem> findByUexItemId(Integer uexItemId);

  /**
   * Returns every non-null {@code external_uuid} in the table. Drives the R4 closure-mode Wiki item
   * sync (§8.4 Mode A): it fetches Wiki detail for exactly the items UEX already placed in {@code
   * game_item} (plus blueprint item references), never enumerating the full ~12 700-row Wiki item
   * pool.
   *
   * @return all distinct non-null external UUIDs
   */
  @Query("SELECT DISTINCT g.externalUuid FROM GameItem g WHERE g.externalUuid IS NOT NULL")
  java.util.List<java.util.UUID> findAllExternalUuids();

  /**
   * Soft-deletes UEX-side ownership of every row whose {@code uex_item_id} is set, NOT included in
   * {@code seenIds}, and whose {@code uex_deleted_at} is currently NULL. Mirrors the {@code
   * MaterialPriceRepository.clearStalePrices} pattern: gated by a non-empty {@code seenIds} so a
   * sync that fails on every row never wipes the entire table.
   *
   * @param seenIds UEX item ids successfully processed in the current run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE GameItem g SET g.uexDeletedAt = :now "
          + "WHERE g.uexItemId IS NOT NULL "
          + "AND g.uexItemId NOT IN :seenIds "
          + "AND g.uexDeletedAt IS NULL")
  int markUexDeletedExcept(
      @Param("seenIds") Collection<Integer> seenIds, @Param("now") Instant now);

  /**
   * Soft-deletes SC Wiki ownership of every <em>Wiki-written</em> row whose {@code external_uuid}
   * is NOT in {@code seenExternalUuids} and that is not already marked. Drives the R5 Mode-B
   * cross-kind orphan sweep (SC_WIKI_SYNC_PLAN.md §8.4 / §8.7). The caller gates this on a
   * non-empty seen set AND on every kind endpoint having fetched successfully, so a transient Wiki
   * outage never wipes the Wiki-side merge state.
   *
   * <p>Unlike {@link de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository}'s vehicle
   * sweep, the gate is {@code scwiki_synced_at IS NOT NULL} (the Wiki actually wrote this row) and
   * NOT {@code external_uuid IS NOT NULL}: a {@code game_item} row created by the UEX item sync
   * also carries an {@code external_uuid}, so gating on its mere presence would wrongly stamp
   * {@code scwiki_deleted_at} on UEX-only items the Wiki has never described.
   *
   * @param seenExternalUuids the external UUIDs the Wiki item backfill touched this run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE GameItem g SET g.scwikiDeletedAt = :now "
          + "WHERE g.scwikiSyncedAt IS NOT NULL "
          + "AND g.externalUuid NOT IN :seenExternalUuids "
          + "AND g.scwikiDeletedAt IS NULL")
  int markScwikiDeletedExcept(
      @Param("seenExternalUuids") Collection<UUID> seenExternalUuids, @Param("now") Instant now);
}
