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
 * <p>The single scheduled method calls the 6 dedicated sync services in a fixed order: universe
 * topology first (factions, jurisdictions, planets, moons, …) so subsequent imports can resolve
 * their parent locations, then star systems / commodities / manufacturers / vehicles, then refinery
 * methods + yields last (depend on commodities). Any single service failure is caught and logged so
 * a half-stale UEX response does not abort the whole sweep.
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

      uexRefinerySyncService.syncRefiningMethods();
      uexRefinerySyncService.syncRefineryYields();
    } catch (Exception e) {
      log.error("Scheduled task for UEX data failed", e);
    }
  }
}
