package de.greluc.krt.iri.basetool.frontend.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Audit M-5: pins that the frontend prod JSON sink scrubs PII (e-mail / JWT / bearer token) before
 * it is written, mirroring the backend encoder test. Before this the frontend JSON appender used
 * the stock {@code LogstashEncoder} and was the only unmasked log output in the system.
 */
class PiiMaskingLogstashEncoderTest {

  private PiiMaskingLogstashEncoder encoder;
  private LoggerContext context;
  private Logger logger;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    // LogstashEncoder reads the MDC at encode time; an empty LoggerContext has no MDCAdapter set,
    // which throws NPE inside MdcJsonProvider. Attach the standard logback MDC adapter so the
    // encoder sees an empty-but-valid MDC.
    context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
    logger = context.getLogger(PiiMaskingLogstashEncoderTest.class);
    encoder = new PiiMaskingLogstashEncoder();
    encoder.setContext(context);
    encoder.start();
  }

  @Test
  void shouldMaskEmailInsideJson() {
    String json = encode("User email is test.user@example.com");
    assertFalse(json.contains("test.user@example.com"), "raw email must not leak: " + json);
    assertTrue(json.contains("***@***.***"), "masked placeholder must be present: " + json);
  }

  @Test
  void shouldMaskJwtInsideJson() {
    String token =
        "eyJhbGciOiJIUzI1NiIsInR5cCI.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    String json = encode("Received JWT " + token);
    assertFalse(json.contains(token), "raw JWT must not leak: " + json);
    assertTrue(json.contains("JWT_***"), "masked placeholder must be present: " + json);
  }

  @Test
  void shouldMaskBearerToken() {
    String json = encode("Authorization: Bearer 1234567890abcdef");
    assertFalse(json.contains("1234567890abcdef"), "bearer secret must not leak: " + json);
    assertTrue(json.contains("Bearer ***"), "masked placeholder must be present: " + json);
  }

  @Test
  void shouldKeepJsonStructureIntact() {
    String json = encode("Email user@example.org used token=my-secret");
    // The masker only inserts alphanumerics, so the surrounding JSON must still start with '{' and
    // end with '}' (LogstashEncoder appends a trailing newline).
    String trimmed = json.trim();
    assertTrue(
        trimmed.startsWith("{") && trimmed.endsWith("}"),
        "encoded output must remain a JSON object: " + json);
    assertFalse(json.contains("user@example.org"));
    assertFalse(json.contains("my-secret"));
  }

  private String encode(String message) {
    ILoggingEvent event =
        new LoggingEvent(
            PiiMaskingLogstashEncoderTest.class.getName(), logger, Level.INFO, message, null, null);
    byte[] raw = encoder.encode(event);
    return new String(raw, StandardCharsets.UTF_8);
  }
}
