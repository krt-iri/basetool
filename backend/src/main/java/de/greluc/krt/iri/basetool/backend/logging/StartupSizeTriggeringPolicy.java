package de.greluc.krt.iri.basetool.backend.logging;

import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import java.io.File;

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
