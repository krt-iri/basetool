package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.model.SyncSourceSystem;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * R4 SC Wiki item sync — closure mode (SC_WIKI_SYNC_PLAN.md §8.4 Mode A). Rather than walking the
 * full ~12 700-row Wiki item pool (that is the R5 backfill), it fetches Wiki detail for exactly the
 * items already in scope:
 *
 * <ul>
 *   <li>every {@code external_uuid} already present in {@code game_item} (i.e. everything the UEX
 *       item sync placed there) — the dominant case; fills {@code classification} / {@code mass} /
 *       {@code description_*} / dimensions on each;
 *   <li>every Wiki item UUID referenced by a blueprint ingredient line that UEX never catalogued —
 *       these become fresh {@code WIKI_ONLY} rows so the blueprint graph resolves.
 * </ul>
 *
 * <p>It hits {@code GET /api/items/{uuid}} per uuid, paced via {@link ScWikiClient}. A {@code null}
 * response (404 — the Wiki doesn't know the item) logs {@link SyncEventType#WIKI_MISSING} and
 * leaves the row {@code UEX_ONLY}. Conflict policy (§6.3.5): the Wiki sync writes only the
 * Wiki-owned descriptive columns and flips {@code source_systems} {@code UEX_ONLY → BOTH}; it does
 * NOT overwrite the UEX-canonical {@code name} or {@code manufacturer} (manufacturer reconciliation
 * is R6). New {@code WIKI_ONLY} rows are created with {@code kind = GENERIC} (precise kind
 * derivation from the Wiki classification is deferred).
 *
 * <p>Closure mode does not soft-delete (no orphan sweep): it never enumerates the full Wiki pool,
 * so a missing uuid means "not in scope", not "deleted upstream". Gated behind {@code
 * krt.scwiki.item-sync-enabled} (default {@code false}); ships dark.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScWikiItemSyncService {

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final GameItemRepository gameItemRepository;
  private final BlueprintRepository blueprintRepository;
  private final SyncReportService syncReportService;

  /** Runs the closure-mode item fill. No-op (with an INFO line) when the feature flag is off. */
  @Transactional
  public void syncItems() {
    if (!Boolean.TRUE.equals(properties.getItemSyncEnabled())) {
      log.info(
          "SC Wiki item sync invoked but disabled (krt.scwiki.item-sync-enabled=false) —"
              + " skipping.");
      return;
    }

    Set<UUID> targets = new LinkedHashSet<>(gameItemRepository.findAllExternalUuids());
    targets.addAll(blueprintRepository.findReferencedItemUuids());
    if (targets.isEmpty()) {
      log.info("SC Wiki item sync: no game_item UUIDs to fill — nothing to do.");
      return;
    }

    log.info("Starting SC Wiki item sync (closure mode) for {} item uuid(s)...", targets.size());
    UUID runId = syncReportService.beginRun();
    Instant now = Instant.now();
    int filled = 0;
    int created = 0;
    int missing = 0;

    for (UUID uuid : targets) {
      scWikiClient.paceForRateLimit();
      try {
        ScWikiItemDto dto =
            scWikiClient.fetchOne(
                properties.getItemsEndpoint() + "/" + uuid, ScWikiItemDto.class, "item");
        if (dto == null) {
          syncReportService.logScwikiEvent(
              runId,
              SyncEventType.WIKI_MISSING,
              "game_item",
              uuid,
              null,
              "GET /api/items/{uuid} returned no item — Wiki does not know this asset.");
          missing++;
          continue;
        }
        GameItem item = gameItemRepository.findByExternalUuid(uuid).orElse(null);
        boolean isNew = item == null;
        if (isNew) {
          item = new GameItem();
          item.setExternalUuid(uuid);
          item.setName(StringUtils.hasText(dto.name()) ? dto.name() : uuid.toString());
          item.setKind(GameItemKind.GENERIC);
          item.setSourceSystems(GameItemSourceSystem.WIKI_ONLY);
          created++;
        } else {
          filled++;
        }
        applyWikiFields(item, dto, now);
        if (item.getSourceSystems() == GameItemSourceSystem.UEX_ONLY) {
          item.setSourceSystems(GameItemSourceSystem.BOTH);
        }
        gameItemRepository.save(item);
      } catch (Exception e) {
        log.error("Failed to fill SC Wiki item {}", uuid, e);
      }
    }

    syncReportService.pruneRuns(SyncSourceSystem.SCWIKI);
    log.info(
        "Finished SC Wiki item sync: {} filled, {} created WIKI_ONLY, {} missing on Wiki.",
        filled,
        created,
        missing);
  }

  /**
   * Writes the Wiki-owned descriptive columns onto a game item. Never touches the UEX-canonical
   * {@code name} (preserved on existing rows; set at creation for new {@code WIKI_ONLY} rows) or
   * {@code manufacturer} (sticky on UEX — R6 reconciles). Multi-language {@code description} is
   * read for {@code en_EN} / {@code de_DE}; {@code size} is parsed defensively into the integer
   * size class.
   *
   * @param item the game item to update
   * @param dto the Wiki item payload
   * @param now timestamp for {@code scwiki_synced_at}
   */
  private void applyWikiFields(GameItem item, ScWikiItemDto dto, Instant now) {
    item.setScwikiSlug(dto.slug());
    item.setClassName(dto.className());
    item.setClassification(dto.classification());
    item.setClassificationLabel(dto.classificationLabel());
    item.setWikiType(dto.type());
    item.setWikiTypeLabel(dto.typeLabel());
    item.setWikiSubType(dto.subType());
    item.setWikiSubTypeLabel(dto.subTypeLabel());
    item.setSizeClass(parseSize(dto.size()));
    item.setGrade(dto.grade());
    item.setRarity(dto.rarity());
    item.setMass(dto.mass());
    if (dto.dimension() != null) {
      item.setDimensionX(dto.dimension().x());
      item.setDimensionY(dto.dimension().y());
      item.setDimensionZ(dto.dimension().z());
    }
    if (dto.description() != null) {
      item.setDescriptionEn(dto.description().get("en_EN"));
      item.setDescriptionDe(dto.description().get("de_DE"));
    }
    item.setIsBaseVariant(dto.isBaseVariant());
    item.setIsCraftable(dto.isCraftable());
    item.setScwikiGameVersionSeen(dto.version());
    item.setScwikiSyncedAt(now);
    item.setScwikiDeletedAt(null);
  }

  /**
   * Parses the Wiki {@code size} token into an integer size class. The Wiki emits it inconsistently
   * (numeric for components, sometimes a non-numeric label or absent for armor); non-numeric /
   * blank input yields {@code null} rather than throwing.
   *
   * @param size raw size token
   * @return the integer size class, or {@code null} if not a plain integer
   */
  private static Integer parseSize(String size) {
    if (!StringUtils.hasText(size)) {
      return null;
    }
    try {
      return Integer.valueOf(size.trim());
    } catch (NumberFormatException e) {
      log.debug("Non-numeric Wiki item size '{}' — leaving size_class null", size);
      return null;
    }
  }
}
