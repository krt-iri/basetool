package de.greluc.krt.iri.basetool.frontend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/**
 * Unit tests for {@link AppVersionAdvice}. The advice has three observable states - present
 * BuildProperties with a real version, present BuildProperties whose version field is blank, and a
 * missing BuildProperties bean entirely (no auto-config, typical for sliced @WebMvcTest runs) - and
 * each must produce a non-{@code null} string so the Thymeleaf sidebar fragment never renders an
 * empty version chip.
 */
class AppVersionAdviceTest {

  @Test
  void appVersion_withBuildPropertiesPresent_returnsVersionString() {
    // Given
    Properties props = new Properties();
    props.setProperty("version", "1.2.3");
    BuildProperties buildProperties = new BuildProperties(props);
    AppVersionAdvice advice = new AppVersionAdvice(Optional.of(buildProperties));

    // When
    String version = advice.appVersion();

    // Then
    assertEquals("1.2.3", version, "Should return the version from BuildProperties");
  }

  @Test
  void appVersion_withBuildPropertiesAbsent_returnsFallback() {
    // Given
    AppVersionAdvice advice = new AppVersionAdvice(Optional.empty());

    // When
    String version = advice.appVersion();

    // Then
    assertEquals(
        AppVersionAdvice.FALLBACK_VERSION,
        version,
        "Should fall back to the dev marker when no bean is wired");
  }

  @Test
  void appVersion_withBlankVersion_returnsFallback() {
    // Given
    Properties props = new Properties();
    props.setProperty("version", "   ");
    BuildProperties buildProperties = new BuildProperties(props);
    AppVersionAdvice advice = new AppVersionAdvice(Optional.of(buildProperties));

    // When
    String version = advice.appVersion();

    // Then
    assertEquals(
        AppVersionAdvice.FALLBACK_VERSION,
        version,
        "A blank version field must not surface as an empty chip in the sidebar");
  }
}
