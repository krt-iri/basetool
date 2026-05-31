package de.greluc.krt.iri.basetool.backend.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Static lint of the backend message bundles. A user-visible string added to one locale but not the
 * other otherwise surfaces only as a raw {@code error.some.key} at render time; a literal umlaut in
 * the German bundle breaks the project's {@code \\uXXXX} encoding rule. Both are pinned here so
 * they fail the build instead of production. Reads the source files under {@code
 * src/main/resources} directly (the Gradle {@code Test} task runs with the module directory as its
 * working directory) so the assertions see the exact committed bytes, not the processed classpath
 * copy.
 */
class MessageBundleConsistencyTest {

  /** German locale bundle under the module's main resources. */
  private static final Path DE = Path.of("src/main/resources/messages_de.properties");

  /** English locale bundle under the module's main resources. */
  private static final Path EN = Path.of("src/main/resources/messages_en.properties");

  /**
   * The seven German umlaut characters that CLAUDE.md requires to be written as {@code \\uXXXX}
   * escapes inside {@code .properties} files. Declared as Java unicode escapes so this source file
   * itself stays pure ASCII.
   */
  private static final String LITERAL_UMLAUTS = "äöüÄÖÜß";

  /**
   * Asserts the German and English bundles declare an identical key set, listing any key that
   * exists in only one of them so the missing translation is obvious from the failure message.
   *
   * @throws IOException if either bundle cannot be read from disk
   */
  @Test
  void germanAndEnglishBundlesDeclareTheSameKeys() throws IOException {
    Set<String> deKeys = keysOf(DE);
    Set<String> enKeys = keysOf(EN);

    assertThat(difference(deKeys, enKeys)).as("keys present in DE but missing in EN").isEmpty();
    assertThat(difference(enKeys, deKeys)).as("keys present in EN but missing in DE").isEmpty();
  }

  /**
   * Asserts the German bundle contains no literal umlaut characters, enforcing the {@code \\uXXXX}
   * escaping rule from CLAUDE.md. Offending lines are reported with their 1-based line number.
   *
   * @throws IOException if the German bundle cannot be read from disk
   */
  @Test
  void germanBundleEscapesUmlautsAsUnicode() throws IOException {
    List<String> offenders = new ArrayList<>();
    List<String> lines = Files.readAllLines(DE, StandardCharsets.UTF_8);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      for (int c = 0; c < line.length(); c++) {
        if (LITERAL_UMLAUTS.indexOf(line.charAt(c)) >= 0) {
          offenders.add((i + 1) + ": " + line);
          break;
        }
      }
    }

    assertThat(offenders)
        .as("German umlauts must be written as \\uXXXX escapes in messages_de.properties")
        .isEmpty();
  }

  /**
   * Parses the declared property keys of a bundle via {@link Properties#load(Reader)}, which
   * handles comments, {@code =}/{@code :}/space separators, line continuations and escapes
   * correctly.
   *
   * @param path the bundle to read
   * @return the bundle's keys in stable sorted order
   * @throws IOException if the bundle cannot be read
   */
  private static Set<String> keysOf(Path path) throws IOException {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return new TreeSet<>(properties.stringPropertyNames());
  }

  /**
   * Computes {@code left \\ right} (the keys in {@code left} absent from {@code right}) as a new
   * sorted set, leaving the inputs untouched.
   *
   * @param left the source key set
   * @param right the key set whose members are excluded
   * @return the elements of {@code left} not contained in {@code right}
   */
  private static Set<String> difference(Set<String> left, Set<String> right) {
    Set<String> result = new TreeSet<>(left);
    result.removeAll(right);
    return result;
  }
}
