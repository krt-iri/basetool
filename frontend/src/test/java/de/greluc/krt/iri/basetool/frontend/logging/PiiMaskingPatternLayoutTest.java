package de.greluc.krt.iri.basetool.frontend.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PiiMaskingPatternLayoutTest {

  private PiiMaskingPatternLayout layout;
  private LoggerContext context;
  private Logger logger;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    logger = context.getLogger(PiiMaskingPatternLayoutTest.class);
    layout = new PiiMaskingPatternLayout();
    layout.setPattern("%msg");
    layout.setContext(context);
    layout.start();
  }

  @Test
  void shouldMaskEmail() {
    // Given
    ILoggingEvent event = createEvent("User email is test.user@example.com and it is sensitive");

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals("User email is ***@***.*** and it is sensitive", result, "Email should be masked");
  }

  @Test
  void shouldMaskTokenKeyword() {
    // Given
    ILoggingEvent event = createEvent("Authorization: Bearer 1234567890abcdef and some other text");

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals("Authorization: Bearer *** and some other text", result, "Token should be masked");
    assertFalse(result.contains("1234567890abcdef"));
  }

  @Test
  void shouldMaskSessionId() {
    // Given
    ILoggingEvent event = createEvent("User logged in with session-id: abcdef-1234-5678");

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals("User logged in with session-id: ***", result, "Session ID should be masked");
    assertFalse(result.contains("abcdef-1234-5678"));
  }

  @Test
  void shouldMaskStandaloneJwt() {
    // Given
    ILoggingEvent event =
        createEvent(
            "Received JWT eyJhbGciOiJIUzI1NiIsInR5cCI.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals("Received JWT JWT_***", result, "JWT should be masked");
  }

  @Test
  void shouldMaskMultipleSecrets() {
    // Given
    ILoggingEvent event = createEvent("Email user@example.org used token=my-secret-token");

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals("Email ***@***.*** used token=***", result, "Both secrets should be masked");
    assertFalse(result.contains("user@example.org"));
    assertFalse(result.contains("my-secret-token"));
  }

  @Test
  void shouldNotAlterNormalText() {
    // Given
    String normalText = "This is a normal log message without any secrets.";
    ILoggingEvent event = createEvent(normalText);

    // When
    String result = layout.doLayout(event);

    // Then
    assertEquals(normalText, result, "Normal text should remain unaltered");
  }

  private ILoggingEvent createEvent(String message) {
    return new LoggingEvent(
        "de.greluc.krt.iri.basetool.frontend.logging.PiiMaskingPatternLayoutTest",
        logger,
        Level.INFO,
        message,
        null,
        null);
  }
}
