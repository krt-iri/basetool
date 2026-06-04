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

import de.greluc.krt.iri.basetool.backend.dto.uex.UexItemDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.UexCategory;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

/**
 * R2 UEX item sync. Iterates the {@code uex_category} reference table (game-related rows only),
 * calls {@code /items?id_category=<n>} per category, and upserts the rows into {@code game_item}.
 *
 * <p>Resolution chain per SC_WIKI_SYNC_PLAN.md §8.3.1, R2 subset (Wiki side not yet wired):
 *
 * <ol>
 *   <li>{@code byUexItemId(dto.id)} — fastest path on a re-sync of the same UEX catalogue.
 *   <li>{@code byExternalUuid(dto.uuid)} — joins any row Wiki may have written first (R4+); no-op
 *       in R2's first deploy because Wiki has not run yet.
 *   <li>{@code null} → create a new row stamped {@link GameItemSourceSystem#UEX_ONLY}.
 * </ol>
 *
 * <p>Kind derivation table per §6.3.1 — driven by the row's {@link UexCategory#getSection()}:
 *
 * <ul>
 *   <li>{@code "Armor"} → {@link GameItemKind#ARMOR}
 *   <li>{@code "Clothing"} or {@code "Undersuits"} → {@link GameItemKind#CLOTHING}
 *   <li>{@code "Personal Weapons"} → {@link GameItemKind#WEAPON} (or {@link
 *       GameItemKind#WEAPON_ATTACHMENT} when category name contains {@code "Attachments"})
 *   <li>{@code "Vehicle Weapons"} → {@link GameItemKind#VEHICLE_WEAPON}
 *   <li>{@code "Liveries"} or {@code "Flair"} → {@link GameItemKind#GENERIC}
 *   <li>{@code "Systems"}, {@code "Utility"}, {@code "Avionics"}, {@code "Propulsion"}, {@code
 *       "Module"}, {@code "Technology"} → {@link GameItemKind#VEHICLE_ITEM}
 *   <li>everything else → {@link GameItemKind#GENERIC}
 * </ul>
 *
 * <p>Orphan handling: rows whose {@code uex_item_id} no longer appears in any category response get
 * their {@code uex_deleted_at} stamped via {@link
 * GameItemRepository#markUexDeletedExcept(java.util.Collection, Instant)}. The sweep is gated on a
 * non-empty seen-id set so a sync that fails on every category never wipes the local catalogue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UexItemSyncService {

  private final UexClient uexClient;
  private final UexCategoryRefService categoryRefService;
  private final GameItemRepository gameItemRepository;
  private final ManufacturerRepository manufacturerRepository;
  private final ShipTypeRepository shipTypeRepository;

  /**
   * Runs the full UEX item sync: ensures the category reference table is fresh, then walks every
   * game-related category. Empty UEX responses short-circuit per category without wiping local
   * data.
   */
  @Transactional
  public void syncItems() {
    log.info("Starting synchronization of UEX items...");
    List<UexCategory> categories = categoryRefService.syncCategories();

    Set<Integer> seenUexItemIds = new HashSet<>();
    int categoriesProcessed = 0;
    int itemsProcessed = 0;
    int itemsCreated = 0;
    Instant now = Instant.now();

    for (UexCategory category : categories) {
      if (!Boolean.TRUE.equals(category.getIsGameRelated())) {
        continue;
      }
      if (!"item".equalsIgnoreCase(category.getType())) {
        continue;
      }
      List<UexItemDto> dtos = uexClient.getItemsForCategory(category.getId());
      if (dtos.isEmpty()) {
        log.debug(
            "No items received for UEX category {} ({}/{})",
            category.getId(),
            category.getSection(),
            category.getName());
        continue;
      }
      categoriesProcessed++;
      for (UexItemDto dto : dtos) {
        try {
          GameItem item = upsertItem(dto, category, now);
          if (item != null) {
            itemsProcessed++;
            if (item.getCreatedAt() == null || item.getCreatedAt().equals(item.getUpdatedAt())) {
              itemsCreated++;
            }
            if (item.getUexItemId() != null) {
              seenUexItemIds.add(item.getUexItemId());
            }
          }
        } catch (Exception e) {
          log.error("Failed to process UEX item dto: {}", dto, e);
        }
      }
    }

    if (seenUexItemIds.isEmpty()) {
      log.warn(
          "Skipping orphan sweep — no UEX item was processed across {} category response(s).",
          categoriesProcessed);
    } else {
      int marked = gameItemRepository.markUexDeletedExcept(seenUexItemIds, now);
      if (marked > 0) {
        log.info("Marked {} game_item row(s) uex_deleted (no longer in UEX feed)", marked);
      }
    }

    // Decode the encoded query parameter for cleaner log output - the actual HTTP request was
    // built with the encoded form by UexClient.
    String reportLabel = UriUtils.decode("UEX item sync", "UTF-8");
    log.info(
        "Finished {}: {} categories visited, {} items upserted ({} new, {} updated)",
        reportLabel,
        categoriesProcessed,
        itemsProcessed,
        itemsCreated,
        itemsProcessed - itemsCreated);
  }

  /**
   * Upserts a single UEX item DTO into the {@code game_item} table.
   *
   * @param dto inbound UEX row
   * @param category resolved category for kind derivation + FK
   * @param now timestamp to stamp on the row
   * @return the persisted entity, or {@code null} if the DTO was unusable
   */
  private GameItem upsertItem(UexItemDto dto, UexCategory category, Instant now) {
    if (dto.id() == null || !StringUtils.hasText(dto.name())) {
      log.debug("Skipping UEX item with missing id/name: {}", dto);
      return null;
    }

    GameItem item = resolveExistingItem(dto);
    boolean newRow = (item == null);
    if (newRow) {
      item = new GameItem();
      item.setSourceSystems(GameItemSourceSystem.UEX_ONLY);
    }

    UUID externalUuid = parseUuid(dto.uuid());
    if (item.getExternalUuid() == null && externalUuid != null) {
      // R2 first write for a row UEX exposes with a UUID. R3 slug-fallback may later
      // backfill external_uuid on rows where UEX returned an empty uuid (the ~30% case).
      item.setExternalUuid(externalUuid);
    } else if (item.getExternalUuid() != null
        && externalUuid != null
        && !item.getExternalUuid().equals(externalUuid)) {
      // UEX provided a UUID that disagrees with the one already stored — keep the existing key
      // (Wiki side may have written it) and log so the admin can investigate.
      log.warn(
          "UEX item {} carries uuid={} but local row already has external_uuid={}",
          dto.id(),
          externalUuid,
          item.getExternalUuid());
    }

    item.setName(dto.name());
    // §6.3.1 more-specific-wins: never downgrade a kind a previous (Wiki or UEX) pass already set
    // to something specific. A cross-listed paint that Wiki filed as VEHICLE_ITEM must not become
    // GENERIC just because UEX catalogues the same external_uuid under a Liveries category. New
    // rows start at GENERIC, so the merge leaves the freshly-derived kind intact.
    item.setKind(GameItemKind.mergeMoreSpecific(item.getKind(), deriveKind(category)));
    item.setManufacturer(resolveManufacturer(dto));
    item.setUexItemId(dto.id());
    item.setUexSlug(dto.slug());
    item.setUexCategory(category);
    item.setUexCompanyId(dto.idCompany());
    item.setUexVehicleId(dto.idVehicle());
    item.setLinkedShipType(resolveLinkedShipType(dto));
    item.setUexColor(dto.color());
    item.setUexColor2(dto.color2());
    item.setUexQuality(dto.quality());
    item.setUexUrlStore(dto.urlStore());
    item.setUexScreenshot(dto.screenshot());
    item.setIsExclusivePledge(asBoolean(dto.isExclusivePledge()));
    item.setIsExclusiveSubscriber(asBoolean(dto.isExclusiveSubscriber()));
    item.setIsExclusiveConcierge(asBoolean(dto.isExclusiveConcierge()));
    item.setUexIsCommodity(asBoolean(dto.isCommodity()));
    item.setUexIsHarvestable(asBoolean(dto.isHarvestable()));
    item.setUexNotification(dto.notification());
    item.setUexSyncedAt(now);
    item.setUexDeletedAt(null);
    item.setUexGameVersionSeen(dto.gameVersion());

    // Promote source_systems UEX_ONLY -> BOTH when Wiki has already written this row (R4+).
    if (item.getSourceSystems() == GameItemSourceSystem.WIKI_ONLY) {
      item.setSourceSystems(GameItemSourceSystem.BOTH);
    }

    return gameItemRepository.save(item);
  }

  /**
   * Resolves a candidate {@link GameItem} for the inbound DTO. Order: by UEX item id (fastest
   * re-resolution path), then by Wiki-shared external UUID (no-op in R2 first deploy), then {@code
   * null}.
   *
   * @param dto inbound UEX row
   * @return existing row if matched; {@code null} otherwise
   */
  private GameItem resolveExistingItem(UexItemDto dto) {
    if (dto.id() != null) {
      Optional<GameItem> byUex = gameItemRepository.findByUexItemId(dto.id());
      if (byUex.isPresent()) {
        return byUex.orElseThrow();
      }
    }
    UUID externalUuid = parseUuid(dto.uuid());
    if (externalUuid != null) {
      Optional<GameItem> byUuid = gameItemRepository.findByExternalUuid(externalUuid);
      if (byUuid.isPresent()) {
        return byUuid.orElseThrow();
      }
    }
    return null;
  }

  /**
   * Looks up the local manufacturer row by UEX company id (fast path), falling back to a
   * case-insensitive name match. Returns {@code null} when the company can't be resolved — the row
   * is still persisted; the FK stays NULL and the admin can fix it via {@code
   * /admin/material-aliases} once that surface is generalised (post-R2).
   *
   * @param dto inbound UEX row
   * @return resolved manufacturer, or {@code null}
   */
  private Manufacturer resolveManufacturer(UexItemDto dto) {
    if (dto.idCompany() != null && dto.idCompany() != 0) {
      Optional<Manufacturer> byId = manufacturerRepository.findByUexCompanyId(dto.idCompany());
      if (byId.isPresent()) {
        return byId.orElseThrow();
      }
    }
    if (StringUtils.hasText(dto.companyName())) {
      return manufacturerRepository.findByNameIgnoreCase(dto.companyName()).orElse(null);
    }
    return null;
  }

  /**
   * Looks up the local {@link ShipType} for vehicle-bound items (paints, components carrying {@code
   * id_vehicle}).
   *
   * @param dto inbound UEX row
   * @return resolved ship type, or {@code null} if {@code id_vehicle} is 0 / unknown
   */
  private ShipType resolveLinkedShipType(UexItemDto dto) {
    if (dto.idVehicle() == null || dto.idVehicle() == 0) {
      return null;
    }
    return shipTypeRepository.findByUexVehicleId(dto.idVehicle()).orElse(null);
  }

  /**
   * Maps the row's category to a {@link GameItemKind} per the §6.3.1 table. The decision is driven
   * by the section first; for Personal Weapons the subcategory name also distinguishes weapons from
   * attachments.
   *
   * @param category resolved category for the row
   * @return derived kind, or {@link GameItemKind#GENERIC} if no specific match applies
   */
  static GameItemKind deriveKind(UexCategory category) {
    if (category == null || category.getSection() == null) {
      return GameItemKind.GENERIC;
    }
    String section = category.getSection().toLowerCase(Locale.ROOT);
    String name = category.getName() == null ? "" : category.getName().toLowerCase(Locale.ROOT);
    return switch (section) {
      case "armor" -> GameItemKind.ARMOR;
      case "clothing", "undersuits" -> GameItemKind.CLOTHING;
      case "personal weapons" ->
          name.contains("attachment") ? GameItemKind.WEAPON_ATTACHMENT : GameItemKind.WEAPON;
      case "vehicle weapons" -> GameItemKind.VEHICLE_WEAPON;
      case "liveries", "flair" -> GameItemKind.GENERIC;
      case "systems", "utility", "avionics", "propulsion", "module", "technology" ->
          GameItemKind.VEHICLE_ITEM;
      default -> GameItemKind.GENERIC;
    };
  }

  /**
   * Parses a UEX-emitted UUID string. UEX returns an empty string for ~30% of rows (Avionics,
   * Decorations, Liveries, Armor); this method returns {@code null} for those instead of throwing.
   *
   * @param raw raw UUID string from the DTO
   * @return parsed UUID, or {@code null} for empty / blank / malformed input
   */
  private static UUID parseUuid(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      log.debug("Skipping malformed UEX uuid '{}': {}", raw, e.getMessage());
      return null;
    }
  }

  /**
   * Normalises UEX's integer 0/1 flag into a {@link Boolean}.
   *
   * @param flag UEX-style 0/1 integer
   * @return {@code true} iff {@code flag} equals 1; {@code null} when input is {@code null}
   */
  private static Boolean asBoolean(Integer flag) {
    if (flag == null) {
      return null;
    }
    return flag == 1;
  }
}
