package de.greluc.krt.iri.basetool.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    executor.initialize();
    return executor;
  }
}
