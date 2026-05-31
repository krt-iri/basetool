package de.greluc.krt.iri.basetool.backend.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Process-wide mutual-exclusion gate shared by the UEX and SC Wiki schedulers so the two external
 * data syncs never run at the same time.
 *
 * <p>Both {@code UexScheduler} and {@code ScWikiScheduler} funnel their whole sweep through {@link
 * #runExclusively(String, Runnable)} on the one shared singleton. The gate is a <b>fair</b> {@link
 * ReentrantLock} acquired with a bounded {@link ReentrantLock#tryLock(long, TimeUnit) timed
 * tryLock}: a tick that finds the lock held <b>waits</b> for the in-flight sync to finish and then
 * runs, rather than being dropped. Because both syncs run only once a day, dropping a blocked tick
 * would mean waiting a full day for the next one — so the blocked sync queues behind the running
 * one and proceeds as soon as it is released.
 *
 * <p>The wait is bounded by {@code krt.sync.coordinator.max-wait-ms} (default 1 h): a normal sync
 * finishes in minutes, so the cap only trips when a sync is genuinely stuck. If the holder does not
 * release within the cap, the waiting tick is skipped (logged) instead of blocking its executor
 * thread indefinitely — that is the hung-sync safety net, and the next daily tick retries.
 *
 * <p>The fair lock guarantees the waiter acquires the gate the moment the holder releases (FIFO),
 * so a UEX and an SC Wiki tick that fire close together always run back-to-back, never interleaved.
 * This removes the concurrent-write races (e.g. the {@code game_item} optimistic-lock collisions)
 * that overlapping UEX and SC Wiki runs used to cause. The schedulers additionally stagger their
 * start offsets so they do not even attempt to start together under normal operation; this gate is
 * the safety net for the case where their daily cadences drift back into alignment.
 */
@Slf4j
@Component
public class SyncCoordinator {

  /**
   * Fair gate so a blocked tick acquires the lock in FIFO order the instant the holder releases.
   */
  private final ReentrantLock lock = new ReentrantLock(true);

  /**
   * How long a blocked tick waits for the in-flight sync before giving up (hung-sync safety net).
   */
  private final long maxWaitMillis;

  /**
   * Label of the sync currently holding the lock, for the wait/skip log lines; {@code null} idle.
   */
  private volatile String activeLabel;

  /**
   * Creates the coordinator with the configured maximum wait for a blocked tick.
   *
   * @param maxWaitMillis upper bound, in milliseconds, that a blocked sync waits for the in-flight
   *     sync to finish before skipping its own run; from {@code krt.sync.coordinator.max-wait-ms}
   *     (default {@code 3600000} = 1 h)
   */
  public SyncCoordinator(@Value("${krt.sync.coordinator.max-wait-ms:3600000}") long maxWaitMillis) {
    this.maxWaitMillis = maxWaitMillis;
  }

  /**
   * Runs {@code task} under the shared lock, waiting for any in-flight sync to finish first.
   *
   * <p>Acquires the gate with a timed {@code tryLock(maxWaitMillis)}. If another sync is running
   * the call blocks until that sync releases the lock (then runs {@code task}) or until the wait
   * cap is reached (then skips, logging a WARN — the holder is presumed stuck). On success the lock
   * is released in a {@code finally} block so a thrown task still frees the gate. The exclusion
   * spans both schedulers, so a running UEX sweep makes a concurrent SC Wiki tick wait and vice
   * versa, and the two never execute at the same time.
   *
   * @param label short human-readable name of the sync ({@code "UEX"} / {@code "SC Wiki"}) used in
   *     the wait/skip log lines and to report which sync is holding the gate
   * @param task the sync sweep to run under the exclusive lock
   * @return {@code true} if the task ran, {@code false} if the wait cap elapsed (or the thread was
   *     interrupted) before the gate could be acquired
   */
  public boolean runExclusively(String label, Runnable task) {
    String holderAtEntry = activeLabel;
    if (holderAtEntry != null) {
      log.info(
          "{} sync is waiting: the {} sync is still running — will start as soon as it finishes.",
          label,
          holderAtEntry);
    }
    boolean acquired;
    try {
      acquired = lock.tryLock(maxWaitMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting to start {} sync — skipping this run.", label);
      return false;
    }
    if (!acquired) {
      log.warn(
          "Skipping {} sync: the {} sync did not finish within {} ms — not starting a concurrent"
              + " run; the next daily tick will retry.",
          label,
          activeLabel == null ? "previous" : activeLabel,
          maxWaitMillis);
      return false;
    }
    try {
      activeLabel = label;
      task.run();
      return true;
    } finally {
      activeLabel = null;
      lock.unlock();
    }
  }
}
