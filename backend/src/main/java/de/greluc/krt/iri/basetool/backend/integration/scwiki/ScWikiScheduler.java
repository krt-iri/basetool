package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.AsyncConfig;
import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler bean that drives the periodic SC Wiki sync.
 *
 * <p>{@code @Scheduled} fixed-delay of {@code krt.scwiki.scheduler-delay} (default 24h); Wiki data
 * changes only on game patches. {@code @Async(AsyncConfig.SCWIKI_EXECUTOR)} runs the sweep on the
 * dedicated bounded pool so a slow Wiki response cannot delay the UEX scheduler.
 *
 * <p>R3 wired the first real sync (commodity merge); R4 adds the vehicle fill, the closure-mode
 * item fill and the blueprint graph. The scheduler checks the {@code krt.scwiki.scheduler-enabled}
 * master switch, then delegates to each sync in dependency order; every sync <b>itself</b>
 * self-guards on its own per-sync feature flag (all default {@code false}) so each stays dark until
 * an operator opts in per the deployment runbook. Later phases (R6 manufacturers) add their calls
 * to this body behind their own flags.
 *
 * <p>Per-sync exceptions are swallowed per step ({@link #runStep}) — same fail-one-succeed-others
 * contract as {@code UexScheduler} — so one failing sync never aborts the others or suppresses the
 * next tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScWikiScheduler {

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final ScWikiCommoditySyncService commoditySyncService;
  private final ScWikiBlueprintSyncService blueprintSyncService;
  private final ScWikiItemSyncService itemSyncService;
  private final ScWikiVehicleSyncService vehicleSyncService;

  /**
   * Periodic SC Wiki sync entry point. Runs on the {@link AsyncConfig#SCWIKI_EXECUTOR} pool so a
   * slow Wiki response cannot delay the UEX scheduler (or any other {@code @Scheduled} bean).
   *
   * <p>If the master switch is off, logs and returns. Otherwise runs each sync in dependency order
   * — commodities (R3) and vehicles fill cross-source rows first, then the closure-mode item fill,
   * then blueprints (whose ingredients resolve against {@code game_item} / {@code material}). Each
   * sync self-guards on its per-sync flag. The {@link ScWikiClient} field is referenced here so the
   * {@code scWikiIntegrationClassesMustWireScWikiClient} ArchUnit rule is satisfied.
   */
  @Async(AsyncConfig.SCWIKI_EXECUTOR)
  @Scheduled(fixedDelayString = "${krt.scwiki.scheduler-delay:86400000}")
  public void scheduleScWikiSync() {
    if (!Boolean.TRUE.equals(properties.getSchedulerEnabled())) {
      log.info("ScWikiScheduler invoked but disabled (krt.scwiki.scheduler-enabled=false) — skip.");
      return;
    }
    log.debug("Running scheduled SC Wiki sync against {}", scWikiClient.getClass().getSimpleName());
    runStep("commodity", commoditySyncService::syncCommodities);
    runStep("vehicle", vehicleSyncService::syncVehicles);
    runStep("item", itemSyncService::syncItems);
    runStep("blueprint", blueprintSyncService::syncBlueprints);
  }

  /**
   * Runs one sync step, swallowing and logging any exception so a single failing sync never aborts
   * the remaining steps or suppresses the next scheduled tick.
   *
   * @param label short name of the step for the error log line
   * @param step the sync invocation
   */
  private void runStep(String label, Runnable step) {
    try {
      step.run();
    } catch (Exception e) {
      log.error("Scheduled SC Wiki {} sync failed", label, e);
    }
  }
}
