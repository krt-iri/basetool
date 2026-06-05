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

package de.greluc.krt.iri.basetool.backend.config;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated bounded executors for {@code @Async} workloads.
 *
 * <p>{@code @EnableAsync} itself lives on {@link UexProperties} (it is the historic single owner of
 * the UEX scheduler timing). Without an explicit {@link Executor} bean Spring falls back to the
 * unbounded {@code SimpleAsyncTaskExecutor}, which spawns a new thread per {@code @Async} call and
 * never reuses or caps them — under a slow UEX upstream that latches into a thread leak. This class
 * publishes {@link #uexExecutor()} as a fixed pool with an abort policy so a stuck sync surfaces as
 * a {@link java.util.concurrent.RejectedExecutionException} in the logs instead of accumulating
 * thread state silently.
 */
@Configuration
public class AsyncConfig {

  /** Spring-bean name of the UEX executor, referenced from {@code @Async("uexExecutor")}. */
  public static final String UEX_EXECUTOR = "uexExecutor";

  /**
   * Spring-bean name of the SC Wiki executor, referenced from {@code @Async("scWikiExecutor")}.
   * Distinct from {@link #UEX_EXECUTOR} so a slow Wiki response cannot starve the UEX sync (and
   * vice-versa).
   */
  public static final String SCWIKI_EXECUTOR = "scWikiExecutor";

  /**
   * Spring-bean name of the P4K import executor, referenced from {@code @Async("importExecutor")}.
   * A single worker thread so catalog imports (preview / apply) run strictly one at a time.
   */
  public static final String IMPORT_EXECUTOR = "importExecutor";

  /**
   * Bounded executor for the periodic UEX sync sweep dispatched by {@link
   * de.greluc.krt.iri.basetool.backend.service.UexScheduler}.
   *
   * <p>Sizing rationale: the sweep runs at most once an hour and is a serial chain of HTTP calls
   * against UEX — there is no fan-out inside a single tick. A core pool of two threads covers the
   * normal single-active-sync case plus one head-room slot if a tick overlaps with a manually
   * triggered sync; a hard cap of four prevents thread accumulation if the upstream stalls and
   * multiple ticks pile up. The 100-slot queue absorbs short bursts (admin-triggered re-sync while
   * the scheduler runs) without engaging the rejection handler.
   *
   * <p>{@link ThreadPoolExecutor.AbortPolicy} (Spring default) is preserved deliberately: a
   * rejection means the queue is full AND the pool is at max threads, which only happens when UEX
   * is so unhealthy that swallowing more work would mask the outage. A loud {@link
   * java.util.concurrent.RejectedExecutionException} in the logs is the desired signal.
   *
   * <p>{@code setWaitForTasksToCompleteOnShutdown(true)} + {@code setAwaitTerminationSeconds(20)}
   * align with the application-wide {@code server.shutdown=graceful} window so an in-flight sync is
   * given a chance to finish before the JVM exits.
   *
   * <p><b>MDC propagation</b> via {@link MdcPropagatingTaskDecorator}: classic {@link ThreadLocal}
   * holders (including SLF4J's {@code MDC}) do not flow across thread boundaries automatically.
   * Without a decorator, every UEX-sync log line would emit empty {@code correlationId} / {@code
   * userId} / {@code orgUnitId} MDC fields because the {@code @Scheduled}-triggered task picks a
   * fresh thread from this pool that never ran the request filter chain. The decorator snapshots
   * the submitting thread's MDC map and restores it on the worker before the runnable runs, then
   * clears MDC afterwards so two consecutive tasks on the same pool thread cannot bleed fields into
   * each other. Scheduled-only triggers carry no inbound request so the captured map is typically
   * empty — but admin-triggered re-syncs and tests do submit from a request thread, and those
   * should keep their correlation id in the resulting log lines.
   *
   * @return configured UEX async executor
   */
  @Bean(name = UEX_EXECUTOR)
  public Executor uexExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("uex-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(20);
    executor.setTaskDecorator(new MdcPropagatingTaskDecorator());
    executor.initialize();
    return executor;
  }

  /**
   * Bounded executor for the periodic SC Wiki sync dispatched by {@code ScWikiScheduler}.
   *
   * <p>Sizing rationale: the Wiki tick fires at most once every 24 hours (default {@code
   * krt.scwiki.scheduler-delay = 86 400 000 ms}) and chains a small fan-out of paginated HTTP calls
   * against the Wiki API. A core pool of two threads covers the normal single-active-sync case plus
   * head-room for an admin-triggered re-sync overlapping with the scheduler; the queue is
   * intentionally narrow (size 0) so an unhealthy Wiki backend cannot pile up multiple ticks
   * waiting silently — instead, the second submission is rejected and surfaces in logs as a {@link
   * java.util.concurrent.RejectedExecutionException}.
   *
   * <p>{@link #MdcPropagatingTaskDecorator} mirrors the UEX executor: classic {@code ThreadLocal}
   * holders (including SLF4J's MDC) do not flow across thread boundaries automatically. Without the
   * decorator, every Wiki-sync log line would lose its correlation id / user id / org-unit id
   * because the {@code @Scheduled}-triggered task hands off to a fresh thread that has never seen
   * the request filter chain.
   *
   * @return configured SC Wiki async executor
   */
  @Bean(name = SCWIKI_EXECUTOR)
  public Executor scWikiExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("scwiki-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(20);
    executor.setTaskDecorator(new MdcPropagatingTaskDecorator());
    executor.initialize();
    return executor;
  }

  /**
   * Single-thread executor for the asynchronous P4K catalog import dispatched by {@code
   * P4kImportJobRunner}.
   *
   * <p>Sizing rationale: a core and max pool of <b>one</b> serializes every import run (preview and
   * apply) so two heavy imports never overlap — the apply path rewrites master-data rows under a
   * transaction, and a second concurrent apply would only add lock contention for no benefit (an
   * admin triggers these by hand, rarely). The 20-slot queue lets a burst of uploads line up behind
   * the active run rather than being rejected; {@link ThreadPoolExecutor.AbortPolicy} only engages
   * once even that backlog is exhausted, surfacing loudly as a {@link
   * java.util.concurrent.RejectedExecutionException}.
   *
   * <p>{@code setAwaitTerminationSeconds(60)} is more generous than the sync executors because an
   * import can legitimately run for minutes; a job still in flight when the JVM exits is left
   * {@code RUNNING} and reconciled to {@code FAILED} on next startup. {@link
   * MdcPropagatingTaskDecorator} keeps the correlation / user / org-unit MDC fields on the worker
   * thread, as for the other executors.
   *
   * @return configured P4K import async executor
   */
  @Bean(name = IMPORT_EXECUTOR)
  public Executor importExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("p4k-import-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.setTaskDecorator(new MdcPropagatingTaskDecorator());
    executor.initialize();
    return executor;
  }

  /**
   * {@link TaskDecorator} that snapshots the submitting thread's SLF4J {@link MDC} context map and
   * restores it on the worker thread before the wrapped runnable runs. Used by {@link
   * #uexExecutor()} so {@code @Async}-dispatched UEX-sync log lines keep the correlation id, user
   * id and org-unit id of the request (or scheduled trigger) that submitted them.
   *
   * <p>The decorator clears MDC in the {@code finally} block to prevent a fresh task picked up by
   * the same pool thread from inheriting the previous task's MDC fields — the executor reuses
   * worker threads, and a missing clear here would bleed correlation ids across unrelated
   * submissions.
   *
   * <p>A {@code null} snapshot (no MDC on the submitting thread — typical for scheduler-triggered
   * runs at JVM startup) is handled explicitly: the worker starts with an empty MDC and clears it
   * the same way at the end. This avoids an NPE inside {@code MDC.setContextMap(null)} which some
   * SLF4J bindings throw.
   */
  static class MdcPropagatingTaskDecorator implements TaskDecorator {

    @NotNull
    @Override
    public Runnable decorate(@NotNull Runnable runnable) {
      Map<String, String> snapshot = MDC.getCopyOfContextMap();
      return () -> {
        try {
          if (snapshot != null) {
            MDC.setContextMap(snapshot);
          }
          runnable.run();
        } finally {
          MDC.clear();
        }
      };
    }
  }
}
