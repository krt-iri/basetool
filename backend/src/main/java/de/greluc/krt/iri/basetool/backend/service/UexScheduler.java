package de.greluc.krt.iri.basetool.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

  @Async
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
