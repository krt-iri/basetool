package de.greluc.krt.iri.basetool.backend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Plain-text logback layout that runs {@link PiiMasker} on the formatted output. Used by the
 * console / file appenders.
 */
public class PiiMaskingPatternLayout extends PatternLayout {

  @Override
  public String doLayout(ILoggingEvent event) {
    return PiiMasker.mask(super.doLayout(event));
  }
}
