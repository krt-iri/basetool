package de.greluc.krt.iri.basetool.backend.integration.scwiki;

import de.greluc.krt.iri.basetool.backend.config.AsyncConfig;
import de.greluc.krt.iri.basetool.backend.config.ScWikiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Skeleton scheduler bean for the SC Wiki sync.
 *
 * <p>R1 ships only the schedule wiring + the no-op guard: every tick is short-circuited when {@code
 * krt.scwiki.scheduler-enabled = false} (the R1 default). The actual sync services ({@code
 * ScWikiCommoditySyncService}, {@code ScWikiBlueprintSyncService}, …) land in R3+ and are called
 * from this method's body once available.
 *
 * <p>The R1 skeleton serves three concrete purposes:
 *
 * <ol>
 *   <li>Locks in the {@code @Async(AsyncConfig.SCWIKI_EXECUTOR)} + fixed-delay wiring. The bean
 *       must depend on {@link ScWikiClient} (the ArchUnit rule {@code
 *       sCWikiIntegrationClassesMustWireScWikiClient} verifies this) so a future R3 maintainer
 *       cannot accidentally ship a sync service in {@code integration.scwiki} without the
 *       dependency on the shared HTTP client.
 *   <li>Pins the property name {@code krt.scwiki.scheduler-delay} (default 24h = 86 400 000 ms) and
 *       {@code krt.scwiki.scheduler-enabled} (R1 default {@code false}). Both are referenced in
 *       {@code SC_WIKI_SYNC_DEPLOYMENT_RUNBOOK.md} §1 — the runbook would be wrong if the names
 *       drifted.
 *   <li>Emits a single INFO log line on every short-circuited tick so operators see proof that the
 *       scheduler is alive but intentionally idle.
 * </ol>
 *
 * <p>Note: this scheduler stays a no-op when the flag is {@code false} <b>even if</b> R3 ships its
 * sync services — the production runbook gates that flag flip behind a soak window. The R3 PR flips
 * the default in {@link ScWikiProperties}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScWikiScheduler {

  /**
   * Held only so the ArchUnit rule {@code sCWikiIntegrationClassesMustWireScWikiClient} (R1) can
   * verify the dependency at compile-time. Once R3 services land, those services receive the client
   * and the scheduler delegates to them; the field stays here as the contractual entry point.
   */
  @SuppressWarnings({"PMD.UnusedPrivateField", "unused"})
  private final ScWikiClient scWikiClient;

  private final ScWikiProperties properties;

  /**
   * Periodic SC Wiki sync entry point. Runs on the {@link AsyncConfig#SCWIKI_EXECUTOR} pool so a
   * slow Wiki response cannot delay the UEX scheduler (or any other {@code @Scheduled} bean).
   *
   * <p>R1 body: if the master switch is off, log and return; otherwise warn the operator that R1
   * has no sync services wired in yet. R3 replaces the warn with the actual sync chain.
   */
  @Async(AsyncConfig.SCWIKI_EXECUTOR)
  @Scheduled(fixedDelayString = "${krt.scwiki.scheduler-delay:86400000}")
  public void scheduleScWikiSync() {
    if (!Boolean.TRUE.equals(properties.getSchedulerEnabled())) {
      log.info("ScWikiScheduler invoked but disabled (krt.scwiki.scheduler-enabled=false) — skip.");
      return;
    }
    log.warn(
        "ScWikiScheduler tick fired with scheduler-enabled=true, but R1 ships no sync services "
            + "yet. Flip the flag back to false until R3 lands the commodity / blueprint / item "
            + "sync services. See SC_WIKI_SYNC_DEPLOYMENT_RUNBOOK.md §1 for the rollout cadence.");
  }
}
