package de.greluc.krt.iri.basetool.frontend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback pattern layout that scrubs PII / secrets from the rendered console / file log line via
 * the shared {@link PiiMasker}. The masking patterns live in {@link PiiMasker} so this layout and
 * {@link PiiMaskingLogstashEncoder} (the prod JSON sink) apply exactly the same rules (audit M-5).
 */
public class PiiMaskingPatternLayout extends PatternLayout {

  @Override
  public String doLayout(ILoggingEvent event) {
    return PiiMasker.mask(super.doLayout(event));
  }
}
