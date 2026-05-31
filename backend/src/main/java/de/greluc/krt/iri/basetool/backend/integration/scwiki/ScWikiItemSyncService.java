package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiItemManufacturerDto;
import de.greluc.krt.iri.basetool.backend.dto.scwiki.ScWikiResponseDto;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.GameItemKind;
import de.greluc.krt.iri.basetool.backend.model.GameItemSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.SyncEventType;
import de.greluc.krt.iri.basetool.backend.model.SyncSourceSystem;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.service.SyncReportService;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * SC Wiki item sync — two modes selected by {@code krt.scwiki.sync-all-items} (SC_WIKI_SYNC_PLAN.md
 * §8.4). Both are gated behind {@code krt.scwiki.item-sync-enabled} (default {@code false}); the
 * service ships dark.
 *
 * <p><b>Mode A — closure (default, {@code sync-all-items=false}, R4):</b> fetches Wiki detail for
 * exactly the items already in scope — every {@code external_uuid} already in {@code game_item}
 * plus every item UUID referenced by an ingested blueprint — via per-UUID {@code GET
 * /api/items/{uuid}}. It never enumerates the full Wiki pool, so it does not soft-delete.
 *
 * <p><b>Mode B — full backfill ({@code sync-all-items=true}, R5):</b> pages every per-kind Wiki
 * endpoint and the residual {@code /api/items} catch-all, deriving {@link GameItemKind} from the
 * source endpoint (§6.3.1) and upserting the whole ~12 700-row pool. Each pass uses a configurable
 * {@code filter[classification]} ({@link ScWikiProperties}) — the {@code /api/armor} endpoint
 * returns the full pool without one (§3.4 quirk #1), so a {@link ScWikiProperties#getArmorFilter()}
 * is required and a {@link ScWikiProperties#getBackfillKindSanityCap() sanity cap} refuses any kind
 * pass that still comes back pool-sized. Conflict policy is unchanged from R4 (§6.3.5): the Wiki
 * sync writes only Wiki-owned descriptive columns and flips {@code source_systems} {@code UEX_ONLY
 * → BOTH}; it never overwrites the UEX-canonical {@code name} or capability flags. {@code
 * manufacturer} is resolved for new {@code WIKI_ONLY} rows against the existing manufacturer table
 * only (no stubs — R6 reconciles); existing rows keep their sticky manufacturer. {@code kind} uses
 * the more-specific-wins tie-breaker and is never downgraded to {@code GENERIC}.
 *
 * <p>Mode B scope note: §8.4 frames Mode B as "iterate the 7 kind endpoints"; this implementation
 * also runs the §6.3.1 catch-all ({@code /api/items everything else → GENERIC}) as a final residual
 * pass so the backfill actually covers the full pool the §11 R5 target describes (paints, cargo,
 * misc UEX never catalogued). The cross-kind orphan sweep (§8.4 / §8.7) fires only when
 * <b>every</b> pass — kind passes and the residual — returned data, so a transient outage, a 304,
 * or a sanity-cap trip never wipes the Wiki-side merge state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScWikiItemSyncService {

  /** Typed envelope reference for the {@code /api/items}-shaped list endpoints used by Mode B. */
  private static final ParameterizedTypeReference<ScWikiResponseDto<ScWikiItemDto>> ITEM_PAGE_TYPE =
      new ParameterizedTypeReference<ScWikiResponseDto<ScWikiItemDto>>() {};

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final GameItemRepository gameItemRepository;
  private final BlueprintRepository blueprintRepository;
  private final ManufacturerRepository manufacturerRepository;
  private final SyncReportService syncReportService;

  /**
   * Self-reference, resolved lazily so the per-item DB writes can be invoked through the Spring
   * proxy. Calling a {@code @Transactional(REQUIRES_NEW)} method via {@code this} is
   * self-invocation and silently skips the new transaction (the CLAUDE.md self-invocation trap);
   * routing the call through this provider's proxy makes every per-item write its own short
   * transaction so a deadlock on one row rolls back only that row.
   */
  private final ObjectProvider<ScWikiItemSyncService> self;

  /**
   * Entry point invoked by {@link ScWikiScheduler}. No-op (with an INFO line) when {@code
   * item-sync-enabled} is off; otherwise dispatches to the full backfill (Mode B) when {@code
   * sync-all-items} is on, or to the closure fill (Mode A) when it is off.
   */
  public void syncItems() {
    if (!Boolean.TRUE.equals(properties.getItemSyncEnabled())) {
      log.info(
          "SC Wiki item sync invoked but disabled (krt.scwiki.item-sync-enabled=false) —"
              + " skipping.");
      return;
    }
    if (Boolean.TRUE.equals(properties.getSyncAllItems())) {
      syncItemsBackfill();
    } else {
      syncItemsClosure();
    }
  }

  /**
   * Mode A (closure): walks only the items already known locally — every {@code external_uuid} in
   * {@code game_item} plus every blueprint-referenced item UUID — fetching each via {@code GET
   * /api/items/{uuid}}. A {@code null} response logs {@link SyncEventType#WIKI_MISSING} and leaves
   * the row {@code UEX_ONLY}. No orphan sweep (the full pool is never enumerated).
   */
  private void syncItemsClosure() {
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
    int deferred = 0;

    for (UUID uuid : targets) {
      // Pace + fetch OUTSIDE any transaction so no game_item write lock is ever held across the
      // HTTP round-trip. The per-item DB write runs in its own REQUIRES_NEW transaction (via the
      // self proxy), so a deadlock rolls back only that item and the loop continues with the rest.
      scWikiClient.paceForRateLimit();
      try {
        ScWikiItemDto dto =
            scWikiClient.fetchOne(
                properties.getItemsEndpoint() + "/" + uuid, ScWikiItemDto.class, "item");
        ClosureOutcome outcome =
            self.getObject().fillClosureItemWithinTransaction(runId, uuid, dto, now);
        if (outcome == ClosureOutcome.FILLED) {
          filled++;
        } else if (outcome == ClosureOutcome.CREATED) {
          created++;
        } else {
          missing++;
        }
      } catch (OptimisticLockingFailureException e) {
        // Expected transient race: a concurrent sync (typically the parallel UEX game_item sync)
        // updated the same row between this REQUIRES_NEW transaction's read and commit. The row is
        // refreshed on the next scheduled closure run, so this is a benign skip — logged at WARN
        // without a stack trace, not as an ERROR.
        deferred++;
        log.warn("Optimistic lock collision filling SC Wiki item {}; deferring to next run", uuid);
      } catch (Exception e) {
        log.error("Failed to fill SC Wiki item {}", uuid, e);
      }
    }

    syncReportService.pruneRuns(SyncSourceSystem.SCWIKI);
    log.info(
        "Finished SC Wiki item sync: {} filled, {} created WIKI_ONLY, {} missing on Wiki, {}"
            + " deferred (lock collision).",
        filled,
        created,
        missing,
        deferred);
  }

  /**
   * Persists one closure-mode item in its own {@code REQUIRES_NEW} transaction so a deadlock or
   * lock-timeout while writing this row rolls back only this item — not the whole sweep — and the
   * caller's loop continues with the next UUID. Invoked through the {@link #self} proxy so Spring's
   * transaction interceptor actually opens the new transaction (a direct {@code this} call would be
   * self-invocation and run in the caller's — here absent — transaction).
   *
   * <p>A {@code null} {@code dto} (the Wiki returned no item) logs {@link
   * SyncEventType#WIKI_MISSING} and leaves the row untouched. Otherwise the row is matched by
   * {@code external_uuid} (created {@code WIKI_ONLY} when absent), its Wiki columns filled and
   * {@code source_systems} flipped {@code UEX_ONLY → BOTH}; the UEX-canonical {@code name} / {@code
   * kind} are never overwritten.
   *
   * @param runId the current run id for the {@code WIKI_MISSING} event
   * @param uuid the in-game asset UUID being filled
   * @param dto the Wiki item payload, or {@code null} if the Wiki did not return the item
   * @param now the shared {@code scwiki_synced_at} timestamp
   * @return {@link ClosureOutcome#FILLED} for an existing row, {@link ClosureOutcome#CREATED} for a
   *     new {@code WIKI_ONLY} row, or {@link ClosureOutcome#MISSING} when the Wiki returned nothing
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClosureOutcome fillClosureItemWithinTransaction(
      UUID runId, UUID uuid, ScWikiItemDto dto, Instant now) {
    if (dto == null) {
      syncReportService.logScwikiEvent(
          runId,
          SyncEventType.WIKI_MISSING,
          "game_item",
          uuid,
          null,
          "GET /api/items/{uuid} returned no item — Wiki does not know this asset.");
      return ClosureOutcome.MISSING;
    }
    GameItem item = gameItemRepository.findByExternalUuid(uuid).orElse(null);
    boolean isNew = item == null;
    if (isNew) {
      item = new GameItem();
      item.setExternalUuid(uuid);
      item.setName(StringUtils.hasText(dto.name()) ? dto.name() : uuid.toString());
      item.setKind(GameItemKind.GENERIC);
      item.setSourceSystems(GameItemSourceSystem.WIKI_ONLY);
    }
    applyWikiFields(item, dto, now);
    if (item.getSourceSystems() == GameItemSourceSystem.UEX_ONLY) {
      item.setSourceSystems(GameItemSourceSystem.BOTH);
    }
    gameItemRepository.save(item);
    return isNew ? ClosureOutcome.CREATED : ClosureOutcome.FILLED;
  }

  /**
   * Outcome of a single closure-mode item upsert, used by {@link #syncItemsClosure()} to tally the
   * run summary without re-reading the row.
   */
  public enum ClosureOutcome {
    /** An existing {@code game_item} row had its Wiki columns filled. */
    FILLED,
    /** A new {@code WIKI_ONLY} row was created for a blueprint-referenced item. */
    CREATED,
    /** The Wiki returned no item for the UUID; a {@code WIKI_MISSING} event was logged. */
    MISSING
  }

  /**
   * Mode B (full backfill): runs every per-kind endpoint pass (most-specific kind first so a
   * cross-listed UUID is claimed by its more-specific kind) then the residual {@code /api/items}
   * {@code GENERIC} catch-all, accumulating one cross-kind seen set. The orphan sweep runs only
   * when every pass succeeded (returned non-empty, non-capped data) and the seen set is non-empty —
   * mirroring the {@code clearStalePrices} non-empty gate so an outage never wipes local data.
   */
  private void syncItemsBackfill() {
    log.info("Starting SC Wiki item sync (FULL BACKFILL mode) — paging every kind endpoint...");
    BackfillContext ctx =
        new BackfillContext(
            syncReportService.beginRun(), Instant.now(), manufacturerRepository.findAll());
    boolean allPassesSucceeded = true;

    for (KindPass pass : kindPasses()) {
      allPassesSucceeded &= runKindPass(pass, ctx);
    }
    // §6.3.1 catch-all: everything not claimed by a kind endpoint becomes GENERIC. Exempt from the
    // sanity cap — it legitimately returns the whole pool.
    allPassesSucceeded &=
        runKindPass(
            new KindPass(properties.getItemsEndpoint(), GameItemKind.GENERIC, null, false), ctx);

    if (allPassesSucceeded && !ctx.seen.isEmpty()) {
      int marked = self.getObject().markBackfillOrphansWithinTransaction(ctx.seen, ctx.now);
      if (marked > 0) {
        log.info(
            "Marked {} game_item row(s) scwiki_deleted (no longer in any Wiki kind feed).", marked);
      }
    } else {
      log.warn(
          "Skipping cross-kind orphan sweep (allPassesSucceeded={}, seenCount={}): a partial,"
              + " empty, 304 or sanity-capped kind fetch must never wipe Wiki-side state.",
          allPassesSucceeded,
          ctx.seen.size());
    }

    syncReportService.pruneRuns(SyncSourceSystem.SCWIKI);
    log.info(
        "Finished SC Wiki item sync (full backfill): {} created WIKI_ONLY, {} linked"
            + " UEX_ONLY→BOTH, {} skipped (junk), {} deferred (lock collision), {} pass(es)"
            + " empty/capped.",
        ctx.created,
        ctx.linked,
        ctx.skipped,
        ctx.deferred,
        ctx.failedPasses);
  }

  /**
   * Runs the Mode-B cross-kind orphan sweep in its own {@code REQUIRES_NEW} transaction. The
   * preceding pass loop is non-transactional (so no row lock is held across the page fetches), but
   * the {@code @Modifying} bulk soft-delete needs an active transaction — this method provides one.
   * Invoked through the {@link #self} proxy. Soft-deletes every Wiki-written {@code game_item}
   * whose {@code external_uuid} was not seen in any kind feed this run.
   *
   * @param seenExternalUuids the external UUIDs every kind pass touched this run
   * @param now the soft-delete timestamp
   * @return the number of rows marked {@code scwiki_deleted}
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int markBackfillOrphansWithinTransaction(Set<UUID> seenExternalUuids, Instant now) {
    return gameItemRepository.markScwikiDeletedExcept(seenExternalUuids, now);
  }

  /**
   * The ordered Mode-B kind passes, most-specific kind first so that when a single UUID surfaces on
   * more than one endpoint (e.g. a Wiki paint appears under both {@code /vehicle-items} and the
   * residual {@code /items}) the first pass to claim it assigns the more-specific kind. The
   * residual {@code GENERIC} {@code /api/items} pass is appended by {@link #syncItemsBackfill()}
   * after this list.
   *
   * @return the kind passes in claim order
   */
  private List<KindPass> kindPasses() {
    return List.of(
        new KindPass(
            properties.getWeaponAttachmentsEndpoint(),
            GameItemKind.WEAPON_ATTACHMENT,
            properties.getWeaponAttachmentsFilter(),
            true),
        new KindPass(
            properties.getWeaponsEndpoint(),
            GameItemKind.WEAPON,
            properties.getWeaponsFilter(),
            true),
        new KindPass(
            properties.getVehicleWeaponsEndpoint(),
            GameItemKind.VEHICLE_WEAPON,
            properties.getVehicleWeaponsFilter(),
            true),
        new KindPass(
            properties.getVehicleItemsEndpoint(),
            GameItemKind.VEHICLE_ITEM,
            properties.getVehicleItemsFilter(),
            true),
        new KindPass(
            properties.getArmorEndpoint(), GameItemKind.ARMOR, properties.getArmorFilter(), true),
        new KindPass(
            properties.getClothesEndpoint(),
            GameItemKind.CLOTHING,
            properties.getClothesFilter(),
            true),
        new KindPass(
            properties.getFoodEndpoint(), GameItemKind.FOOD, properties.getFoodFilter(), true));
  }

  /**
   * Pages one endpoint, applies the §3.4 sanity-cap guard (for kind passes), then upserts every
   * fresh UUID (skipping UUIDs already claimed by an earlier, more-specific pass).
   *
   * @param pass the endpoint / kind / filter tuple to run
   * @param ctx the shared backfill state (seen set, counters, manufacturer cache)
   * @return {@code true} if the pass fetched usable data and was ingested; {@code false} if the
   *     response was empty / 304 or the sanity cap tripped (caller suppresses the orphan sweep)
   */
  private boolean runKindPass(KindPass pass, BackfillContext ctx) {
    Map<String, String> filters =
        StringUtils.hasText(pass.classificationFilter())
            ? Map.of("classification", pass.classificationFilter())
            : null;
    String label = pass.kind().name().toLowerCase(Locale.ROOT) + " items";
    List<ScWikiItemDto> fetched =
        scWikiClient.fetchAllPages(pass.endpoint(), ITEM_PAGE_TYPE, label, null, filters);

    if (fetched.isEmpty()) {
      log.warn(
          "Wiki kind pass {} ({}) returned no rows (empty / 304) — skipping it; no orphan sweep"
              + " this run.",
          pass.endpoint(),
          pass.kind());
      ctx.failedPasses++;
      return false;
    }
    if (pass.applySanityCap() && fetched.size() > properties.getBackfillKindSanityCap()) {
      log.error(
          "Wiki kind pass {} ({}) returned {} rows, exceeding the sanity cap {} — assuming the"
              + " §3.4 full-pool quirk (missing/blank filter[classification]) and SKIPPING it to"
              + " avoid mis-filing the whole pool under {}.",
          pass.endpoint(),
          pass.kind(),
          fetched.size(),
          properties.getBackfillKindSanityCap(),
          pass.kind());
      ctx.failedPasses++;
      return false;
    }

    for (ScWikiItemDto dto : fetched) {
      if (dto.uuid() == null || !ctx.seen.add(dto.uuid())) {
        continue; // no UUID, or already claimed by an earlier (more-specific) pass
      }
      try {
        // Resolve the manufacturer from the in-memory cache OUTSIDE the write transaction, then
        // persist the row in its own REQUIRES_NEW transaction (via the self proxy) so a deadlock
        // rolls back only this row and the pass keeps going.
        Manufacturer resolvedManufacturer = ctx.resolveManufacturer(dto.manufacturer());
        BackfillOutcome outcome =
            self.getObject()
                .upsertBackfillItemWithinTransaction(
                    dto, pass.kind(), ctx.runId, ctx.now, resolvedManufacturer);
        if (outcome == BackfillOutcome.CREATED) {
          ctx.created++;
        } else if (outcome == BackfillOutcome.LINKED) {
          ctx.linked++;
        } else if (outcome == BackfillOutcome.SKIPPED) {
          ctx.skipped++;
        }
      } catch (OptimisticLockingFailureException e) {
        // Expected transient race with the parallel UEX game_item sync (see syncItemsClosure); the
        // row is refreshed on the next run, so defer it with a WARN rather than an ERROR.
        ctx.deferred++;
        log.warn(
            "Optimistic lock collision upserting SC Wiki item {} ({}); deferring to next run",
            dto.uuid(),
            pass.kind());
      } catch (Exception e) {
        log.error("Failed to upsert SC Wiki item {} ({})", dto.uuid(), pass.kind(), e);
      }
    }
    return true;
  }

  /**
   * Upserts a single Wiki item under the given kind in its own {@code REQUIRES_NEW} transaction so
   * a deadlock or lock-timeout on this row rolls back only this row — not the whole backfill — and
   * the pass loop continues. Invoked through the {@link #self} proxy (a direct {@code this} call
   * would be self-invocation and skip the new transaction). The pass loop pages the Wiki and
   * resolves the manufacturer beforehand, outside this transaction, so the per-row lock window is
   * milliseconds.
   *
   * <p>A brand-new row is created {@code WIKI_ONLY} (its name screened by {@link
   * #shouldSkipNewItem(ScWikiItemDto)}; manufacturer supplied by the caller from the pre-loaded
   * cache) and logs {@link SyncEventType#CREATED_WIKI_ONLY}. An existing row has its Wiki columns
   * filled, its kind merged via {@link GameItemKind#mergeMoreSpecific} and its {@code
   * source_systems} flipped {@code UEX_ONLY → BOTH}; the UEX-canonical {@code name} and (on
   * existing rows) {@code manufacturer} are never touched.
   *
   * @param dto the Wiki item payload
   * @param passKind the kind derived from the source endpoint
   * @param runId the current run id for the sync-report events
   * @param now the shared {@code scwiki_synced_at} timestamp
   * @param resolvedManufacturer the manufacturer resolved from the cache (applied only to new
   *     rows), or {@code null}
   * @return the {@link BackfillOutcome} describing what happened to the row
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public BackfillOutcome upsertBackfillItemWithinTransaction(
      ScWikiItemDto dto,
      GameItemKind passKind,
      UUID runId,
      Instant now,
      Manufacturer resolvedManufacturer) {
    GameItem item = gameItemRepository.findByExternalUuid(dto.uuid()).orElse(null);
    if (item == null) {
      if (shouldSkipNewItem(dto)) {
        syncReportService.logScwikiEvent(
            runId,
            SyncEventType.SKIP_JUNK,
            "game_item",
            dto.uuid(),
            dto.name(),
            "Wiki item failed the new-row guard (blank/markup name or NOITEM_Vehicle); not"
                + " created.");
        return BackfillOutcome.SKIPPED;
      }
      item = new GameItem();
      item.setExternalUuid(dto.uuid());
      item.setName(dto.name().trim());
      item.setKind(passKind);
      item.setSourceSystems(GameItemSourceSystem.WIKI_ONLY);
      item.setManufacturer(resolvedManufacturer);
      applyWikiFields(item, dto, now);
      gameItemRepository.save(item);
      syncReportService.logScwikiEvent(
          runId,
          SyncEventType.CREATED_WIKI_ONLY,
          "game_item",
          dto.uuid(),
          item.getName(),
          "New " + passKind + " row from the full Wiki item backfill.");
      return BackfillOutcome.CREATED;
    }

    item.setKind(GameItemKind.mergeMoreSpecific(item.getKind(), passKind));
    applyWikiFields(item, dto, now);
    BackfillOutcome outcome = BackfillOutcome.UPDATED;
    if (item.getSourceSystems() == GameItemSourceSystem.UEX_ONLY) {
      item.setSourceSystems(GameItemSourceSystem.BOTH);
      outcome = BackfillOutcome.LINKED;
    }
    gameItemRepository.save(item);
    return outcome;
  }

  /** Outcome of a single Mode-B backfill upsert, used by {@link #runKindPass} to tally counters. */
  public enum BackfillOutcome {
    /** A new {@code WIKI_ONLY} row was created. */
    CREATED,
    /** An existing {@code UEX_ONLY} row was linked to the Wiki ({@code UEX_ONLY → BOTH}). */
    LINKED,
    /** An existing {@code BOTH} / {@code WIKI_ONLY} row had its Wiki columns refreshed. */
    UPDATED,
    /** The Wiki row failed the junk-name guard and no row was created. */
    SKIPPED
  }

  /**
   * Writes the Wiki-owned descriptive columns onto a game item. Never touches the UEX-canonical
   * {@code name} (preserved on existing rows; set at creation for new {@code WIKI_ONLY} rows) or,
   * on existing rows, {@code manufacturer} (sticky on UEX — R6 reconciles). Multi-language {@code
   * description} is read for {@code en_EN} / {@code de_DE}; {@code size} is parsed defensively into
   * the integer size class.
   *
   * <p><b>Deliberate deviation from §6.3.5:</b> the plan's length-based name-preference (prefer the
   * longer, better-cased Wiki name when {@code wiki.name.length > existing.name.length}) is
   * intentionally NOT implemented. Keeping the UEX {@code name} unconditionally removes every path
   * for a Wiki sync to overwrite a UEX-canonical field — safer than the literal rule, at the cost
   * of occasionally surfacing UEX's terser name. Revisit only if a concrete UI need for the longer
   * Wiki name emerges.
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

  /**
   * Decides whether a Wiki item should NOT create a new {@code WIKI_ONLY} row (existing rows are
   * always filled). Mirrors the §8.9 junk heuristic for items: a blank name, a name carrying markup
   * ({@code <} / {@code >}, e.g. {@code "<= PLACEHOLDER =>"} or {@code CO<font…>}), or a {@code
   * NOITEM_Vehicle} type (vehicle shells surfaced in {@code /api/items}; §3.4 #7 — they belong to
   * the vehicle sync, not {@code game_item}) is dropped.
   *
   * @param dto the Wiki item payload
   * @return {@code true} if no new row should be created for this item
   */
  private static boolean shouldSkipNewItem(ScWikiItemDto dto) {
    if (!StringUtils.hasText(dto.name())) {
      return true;
    }
    String name = dto.name().trim();
    if (name.contains("<") || name.contains(">")) {
      return true;
    }
    return "NOITEM_Vehicle".equalsIgnoreCase(dto.type());
  }

  /**
   * One Mode-B endpoint pass: the Wiki list endpoint, the {@link GameItemKind} every row from it
   * receives, the optional {@code filter[classification]} value, and whether the §3.4 sanity cap
   * applies (true for the seven kind endpoints, false for the residual {@code /api/items} pass).
   *
   * @param endpoint Wiki list endpoint path
   * @param kind the kind assigned to rows from this endpoint
   * @param classificationFilter the {@code filter[classification]} value, or {@code null} / blank
   * @param applySanityCap whether to refuse a pool-sized response (kind passes) or allow it
   *     (residual)
   */
  private record KindPass(
      String endpoint, GameItemKind kind, String classificationFilter, boolean applySanityCap) {}

  /**
   * Mutable per-run state threaded through the Mode-B passes: the sync-report run id, the shared
   * timestamp, the cross-kind seen set, the in-memory manufacturer lookup (loaded once to keep new
   * {@code WIKI_ONLY} rows from issuing a DB query per row), and the result counters.
   */
  private static final class BackfillContext {
    private final UUID runId;
    private final Instant now;
    private final Set<UUID> seen = new HashSet<>();
    private final Map<UUID, Manufacturer> manufacturersByScwikiUuid = new HashMap<>();
    private final Map<String, Manufacturer> manufacturersByLowerName = new HashMap<>();
    private int created;
    private int linked;
    private int skipped;
    private int deferred;
    private int failedPasses;

    /**
     * Captures the run id and timestamp and indexes the manufacturer table by Wiki UUID and by
     * lower-cased name for in-memory resolution.
     *
     * @param runId the sync-report run id
     * @param now the shared {@code scwiki_synced_at} timestamp
     * @param manufacturers every manufacturer row, loaded once
     */
    BackfillContext(UUID runId, Instant now, List<Manufacturer> manufacturers) {
      this.runId = runId;
      this.now = now;
      for (Manufacturer mfr : manufacturers) {
        if (mfr.getScwikiUuid() != null) {
          manufacturersByScwikiUuid.putIfAbsent(mfr.getScwikiUuid(), mfr);
        }
        if (StringUtils.hasText(mfr.getName())) {
          manufacturersByLowerName.putIfAbsent(mfr.getName().trim().toLowerCase(Locale.ROOT), mfr);
        }
      }
    }

    /**
     * Resolves a Wiki item's nested manufacturer against the pre-loaded table only — by Wiki UUID
     * first, then case-insensitive name. Returns {@code null} (never creates a stub) when nothing
     * matches, leaving the link for the R6 manufacturer reconciliation.
     *
     * @param dto the item's nested manufacturer reference, may be {@code null}
     * @return the matching local manufacturer, or {@code null}
     */
    private Manufacturer resolveManufacturer(ScWikiItemManufacturerDto dto) {
      if (dto == null) {
        return null;
      }
      if (dto.uuid() != null) {
        Manufacturer byUuid = manufacturersByScwikiUuid.get(dto.uuid());
        if (byUuid != null) {
          return byUuid;
        }
      }
      if (StringUtils.hasText(dto.name())) {
        return manufacturersByLowerName.get(dto.name().trim().toLowerCase(Locale.ROOT));
      }
      return null;
    }
  }
}
