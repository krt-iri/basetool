package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Single scheduler bean that drives the periodic UEX-data refresh.
 *
 * <p>{@code @Scheduled} fixed-delay of {@code krt.uex.scheduler-delay} (default 3600000 ms = 1 h).
 * The {@code @Async(AsyncConfig.UEX_EXECUTOR)} pulls execution off the scheduler thread onto the
 * dedicated bounded executor declared in {@link AsyncConfig#uexExecutor()}, so a slow UEX response
 * cannot delay other scheduled tasks AND cannot spawn unbounded threads (the previous unqualified
 * {@code @Async} fell back to the unbounded {@code SimpleAsyncTaskExecutor}).
 * {@code @ConditionalOnProperty(matchIfMissing = true)} keeps the bean active by default but lets
 * the {@code test} profile disable it without touching the rest of the wiring.
 *
 * <p>R2 expansion: the original chain (universe topology → commodities → manufacturers → vehicles →
 * refinery) is extended with {@code UexCategoryRefService.syncCategories()} and {@code
 * UexItemSyncService.syncItems()} between manufacturers and refinery. The order matters: categories
 * must land before items (the item walk reads them); manufacturers must land before items (the item
 * upsert resolves the manufacturer FK); vehicles must land before items because vehicle-bound items
 * (paints, components) resolve {@code linked_ship_type_id} via {@code
 * ShipTypeRepository.findByUexVehicleId}.
 *
 * <p>R7 expansion: {@code UexItemPriceSyncService.syncItemPrices()} runs after the item catalogue
 * (it resolves {@code game_item} + {@code terminal} FKs, both synced earlier in the tick), behind
 * its own {@code krt.uex.item-price-sync-enabled} flag (default off) so it stays a no-op until an
 * operator opts in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "krt.uex",
    name = "scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UexScheduler {

  private final UexCommodityService uexCommodityService;
  private final UexStarSystemService uexStarSystemService;
  private final UexManufacturerService uexManufacturerService;
  private final UexVehicleService uexVehicleService;
  private final UexUniverseSyncService uexUniverseSyncService;
  private final UexRefinerySyncService uexRefinerySyncService;
  private final UexCategoryRefService uexCategoryRefService;
  private final UexItemSyncService uexItemSyncService;
  private final UexItemPriceSyncService uexItemPriceSyncService;

  /**
   * Runs the full UEX sync sweep on a fixed delay. Order matters — topology imports first so later
   * imports can resolve parent locations. Exceptions are swallowed at the top level so a single
   * failing service does not abort the remaining ones.
   */
  @Async(AsyncConfig.UEX_EXECUTOR)
  @Scheduled(fixedDelayString = "${krt.uex.scheduler-delay:3600000}")
  public void scheduleCommodityPriceUpdate() {
    log.info("Running scheduled task to update UEX data...");
    try {
      uexUniverseSyncService.syncFactions();
      uexUniverseSyncService.syncJurisdictions();
      uexUniverseSyncService.syncPlanets();
      uexUniverseSyncService.syncMoons();
      uexUniverseSyncService.syncOrbits();
      uexUniverseSyncService.syncCities();
      uexUniverseSyncService.syncOutposts();
      uexUniverseSyncService.syncPois();
      uexUniverseSyncService.syncSpaceStations();
      uexUniverseSyncService.syncTerminals();

      uexStarSystemService.fetchAndProcessStarSystems();
      uexCommodityService.fetchAndProcessCommoditiesPrices();
      uexManufacturerService.syncManufacturers();
      uexVehicleService.syncVehicles();

      // R2 — category reference + item catalogue. categoriesRef populates the table that
      // UexItemSyncService iterates; the latter resolves manufacturers (already synced above)
      // and linked ship types (also above), so the topological order is preserved.
      uexCategoryRefService.syncCategories();
      uexItemSyncService.syncItems();

      // R7 — item prices. Runs after the item catalogue (resolves game_item) and terminals
      // (synced in the universe phase above). Self-guards on krt.uex.item-price-sync-enabled, so
      // this is a no-op until an operator opts in.
      uexItemPriceSyncService.syncItemPrices();

      uexRefinerySyncService.syncRefiningMethods();
      uexRefinerySyncService.syncRefineryYields();
    } catch (Exception e) {
      log.error("Scheduled task for UEX data failed", e);
    }
  }
}
