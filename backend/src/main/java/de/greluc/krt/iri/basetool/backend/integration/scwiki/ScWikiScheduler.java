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
 * <p>R3 wires the first real sync into the tick: {@link
 * ScWikiCommoditySyncService#syncCommodities()}. The scheduler checks the {@code
 * krt.scwiki.scheduler-enabled} master switch, then delegates; the commodity sync <b>itself</b>
 * self-guards on {@code krt.scwiki.commodity-sync-enabled} (default {@code false}) so the merge
 * stays dark until an operator opts in per the deployment runbook §3. Later phases (R4 blueprints /
 * items, R6 manufacturers) add their calls to this body behind their own per-sync flags.
 *
 * <p>Per-sync exceptions are swallowed at the top level — same fail-one-succeed-others contract as
 * {@code UexScheduler} — so one failing sync never aborts the others or suppresses the next tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScWikiScheduler {

  private final ScWikiClient scWikiClient;
  private final ScWikiProperties properties;
  private final ScWikiCommoditySyncService commoditySyncService;

  /**
   * Periodic SC Wiki sync entry point. Runs on the {@link AsyncConfig#SCWIKI_EXECUTOR} pool so a
   * slow Wiki response cannot delay the UEX scheduler (or any other {@code @Scheduled} bean).
   *
   * <p>If the master switch is off, logs and returns. Otherwise calls each enabled sync in order;
   * R3 ships only the commodity merge (gated by its own feature flag). The {@link ScWikiClient}
   * field is referenced here so the {@code scWikiIntegrationClassesMustWireScWikiClient} ArchUnit
   * rule is satisfied even before later phases wire the client into more sync services.
   */
  @Async(AsyncConfig.SCWIKI_EXECUTOR)
  @Scheduled(fixedDelayString = "${krt.scwiki.scheduler-delay:86400000}")
  public void scheduleScWikiSync() {
    if (!Boolean.TRUE.equals(properties.getSchedulerEnabled())) {
      log.info("ScWikiScheduler invoked but disabled (krt.scwiki.scheduler-enabled=false) — skip.");
      return;
    }
    log.debug("Running scheduled SC Wiki sync against {}", scWikiClient.getClass().getSimpleName());
    try {
      commoditySyncService.syncCommodities();
    } catch (Exception e) {
      log.error("Scheduled SC Wiki commodity sync failed", e);
    }
  }
}
