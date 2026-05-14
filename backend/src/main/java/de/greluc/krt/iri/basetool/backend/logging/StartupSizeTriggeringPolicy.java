package de.greluc.krt.iri.basetool.backend.logging;

import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import java.io.File;

/**
 * Logback triggering policy that forces a log-file rollover on the very first event of a new
 * process if the active file already exists with content.
 *
 * <p>Without this, restarting the application would append to the previous run's log file and make
 * post-mortem analysis hard (no clean boundary between runs). After the first triggered rollover
 * the policy delegates to {@link SizeBasedTriggeringPolicy} so size-based rollover keeps working
 * for the rest of the process lifetime.
 *
 * @param <E> Logback event type
 */
public class StartupSizeTriggeringPolicy<E> extends SizeBasedTriggeringPolicy<E> {
  private boolean started = false;

  @Override
  public boolean isTriggeringEvent(File activeFile, E event) {
    if (!started && activeFile.exists() && activeFile.length() > 0) {
      started = true;
      return true;
    }
    started = true;
    return super.isTriggeringEvent(activeFile, event);
  }
}
