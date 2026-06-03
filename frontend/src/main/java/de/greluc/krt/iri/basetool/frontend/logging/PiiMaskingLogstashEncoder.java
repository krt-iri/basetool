package de.greluc.krt.iri.basetool.frontend.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * {@link LogstashEncoder} extension that masks PII / secrets in the serialized JSON output via
 * {@link PiiMasker} before it is written to the appender. Mirrors the backend encoder of the same
 * name — audit M-5: the prod JSON sink ({@code logs/frontend.json}) previously used the stock
 * {@code LogstashEncoder} with no masking, making it the only unmasked log output in the system.
 *
 * <p>The base {@code LogstashEncoder} has no extension point that operates on the fully rendered
 * JSON, so this encoder post-processes the byte array. Since the masker only ever produces
 * alphanumeric replacements (no quotes, backslashes or control characters), applying it to a
 * serialized JSON document leaves the JSON syntactically valid — the affected string values get
 * replaced 1:1 with shorter placeholders.
 */
public class PiiMaskingLogstashEncoder extends LogstashEncoder {

  @Override
  public byte[] encode(ILoggingEvent event) {
    byte[] raw = super.encode(event);
    if (raw == null || raw.length == 0) {
      return raw;
    }
    String json = new String(raw, StandardCharsets.UTF_8);
    String masked = PiiMasker.mask(json);
    // Compare by value, not reference: PiiMasker.mask only allocates a new String when it actually
    // scrubbed something, so an equal result means "no PII" and we return the original bytes to
    // avoid a needless re-encode round-trip per log event.
    if (masked.equals(json)) {
      return raw;
    }
    return masked.getBytes(StandardCharsets.UTF_8);
  }
}
