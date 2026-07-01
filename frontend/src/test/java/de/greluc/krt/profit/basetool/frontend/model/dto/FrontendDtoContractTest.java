/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/**
 * Cross-module contract test for the hand-mirrored frontend DTOs (S9, part of #905).
 *
 * <p>The frontend re-declares ~200 backend DTOs by hand with no shared module, no {@code
 * project(":backend")} dependency and no code generation — so a backend field rename, removal or
 * enum change would surface only at runtime as a template miss. This test is the drift gate: it
 * diffs every frontend {@code model/dto} record's component set against the committed {@code
 * backend/src/main/resources/api/openapi.json} schema of the same name — structurally, with no
 * classpath coupling to the backend.
 *
 * <p>It fails when:
 *
 * <ul>
 *   <li><b>Shape divergence</b> — a frontend record component has no matching property in the
 *       backend schema (the backend renamed or removed the field, and the frontend would read
 *       {@code null}).
 *   <li><b>Un-annotated enum-to-String demotion</b> — the schema types a property as an enum but
 *       the frontend mirrors it as a bare {@code String} without {@link BackendEnumAsString}. The
 *       frontend deliberately demotes some enums to drive i18n keys; the annotation makes that a
 *       choice, not silent drift.
 *   <li><b>Typed-enum value drift</b> — the frontend keeps a property as a typed enum but the
 *       backend enum has gained a value the frontend enum lacks (which would break
 *       deserialization).
 *   <li><b>Stale annotation</b> — a component carries {@link BackendEnumAsString} but the schema no
 *       longer types the property as an enum.
 * </ul>
 *
 * <p>Frontend-only DTOs (records whose simple name has no matching schema — forms, view models) are
 * out of the contract and skipped; their count is asserted to be a small minority so a broken scan
 * or a mass rename cannot silently empty the check.
 */
class FrontendDtoContractTest {

  /** Package holding the hand-mirrored frontend DTO records. */
  private static final String DTO_PACKAGE = "de.greluc.krt.profit.basetool.frontend.model.dto";

  /** Candidate locations of the committed OpenAPI document, relative to the test working dir. */
  private static final List<Path> OPENAPI_CANDIDATES =
      List.of(
          Path.of("..", "backend", "src", "main", "resources", "api", "openapi.json"),
          Path.of("backend", "src", "main", "resources", "api", "openapi.json"));

  @Test
  void frontendDtosMatchTheOpenApiContract() throws IOException {
    JsonNode schemas = loadOpenApiSchemas();
    List<Class<?>> records = frontendDtoRecords();
    assertThat(records)
        .as("classpath scan of %s should find the hand-mirrored DTO records", DTO_PACKAGE)
        .hasSizeGreaterThan(100);

    List<String> problems = new ArrayList<>();
    int contractChecked = 0;
    int frontendOnly = 0;

    for (Class<?> record : records) {
      JsonNode schema = schemas.get(record.getSimpleName());
      if (schema == null || !schema.hasNonNull("properties")) {
        frontendOnly++;
        continue;
      }
      contractChecked++;
      JsonNode properties = schema.get("properties");
      for (RecordComponent component : record.getRecordComponents()) {
        checkComponent(record, component, properties, problems);
      }
    }

    assertThat(contractChecked)
        .as("a meaningful share of frontend DTOs should map onto an openapi.json schema")
        .isGreaterThan(100);
    assertThat(frontendOnly)
        .as(
            "frontend-only DTOs (no matching schema) should stay a minority of the %d records",
            records.size())
        .isLessThan(records.size() / 2);

    assertThat(problems)
        .as(
            "Frontend DTOs drifted from the backend openapi.json contract. Fix the frontend mirror,"
                + " add @BackendEnumAsString where a String demotion is intended, or regenerate"
                + " openapi.json if the backend legitimately changed:%n%s",
            String.join(System.lineSeparator(), problems))
        .isEmpty();
  }

  /**
   * Runs the four contract checks (shape, un-annotated demotion, typed-enum value drift, stale
   * annotation) for a single record component against its schema property, appending a
   * human-readable line to {@code problems} for every violation.
   *
   * @param record the frontend DTO record being checked
   * @param component the record component (field) under inspection
   * @param properties the {@code properties} node of the matching openapi schema
   * @param problems accumulator for violation messages
   */
  private void checkComponent(
      Class<?> record, RecordComponent component, JsonNode properties, List<String> problems) {
    String qualified = record.getSimpleName() + "." + component.getName();
    JsonNode property = properties.get(component.getName());
    boolean annotated = component.getAnnotation(BackendEnumAsString.class) != null;

    if (property == null) {
      problems.add(
          qualified
              + " — no matching property in the openapi schema (backend renamed/removed it?).");
      return;
    }

    boolean schemaEnum = property.hasNonNull("enum");
    Class<?> type = component.getType();

    if (annotated && !schemaEnum) {
      problems.add(
          qualified
              + " — carries @BackendEnumAsString but openapi no longer types it as an enum; drop"
              + " the annotation.");
    }

    if (!schemaEnum) {
      return;
    }

    if (type == String.class) {
      if (!annotated) {
        problems.add(
            qualified
                + " — openapi types this as an enum "
                + enumValues(property)
                + " but the frontend mirrors it as String without @BackendEnumAsString. Add the"
                + " annotation to choose the demotion, or use the typed enum.");
      }
    } else if (type.isEnum()) {
      Set<String> frontendValues = new LinkedHashSet<>();
      for (Object constant : type.getEnumConstants()) {
        frontendValues.add(((Enum<?>) constant).name());
      }
      Set<String> missing = new TreeSet<>(enumValueSet(property));
      missing.removeAll(frontendValues);
      if (!missing.isEmpty()) {
        problems.add(
            qualified
                + " — backend enum has value(s) "
                + missing
                + " that the frontend enum "
                + type.getSimpleName()
                + " lacks; deserialization of those would fail.");
      }
    } else {
      problems.add(
          qualified
              + " — openapi types this as an enum but the frontend component is "
              + type.getSimpleName()
              + " (neither the typed enum nor an annotated String).");
    }
  }

  /**
   * Reflectively enumerates every {@code record} class in {@link #DTO_PACKAGE} via a Spring
   * classpath scan with a match-all filter (the DTOs are plain records, not Spring components).
   *
   * @return the frontend DTO record classes
   */
  private List<Class<?>> frontendDtoRecords() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((metadataReader, factory) -> true);
    List<Class<?>> records = new ArrayList<>();
    scanner
        .findCandidateComponents(DTO_PACKAGE)
        .forEach(
            beanDefinition -> {
              try {
                Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
                if (clazz.isRecord()) {
                  records.add(clazz);
                }
              } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(
                    "Scanned DTO class could not be loaded: " + beanDefinition.getBeanClassName(),
                    ex);
              }
            });
    return records;
  }

  /**
   * Loads {@code components.schemas} from the committed OpenAPI document, trying each candidate
   * path so the test works whether Gradle runs it from the module dir or the repo root.
   *
   * @return the {@code components.schemas} node keyed by backend DTO simple name
   * @throws IOException when the document cannot be read
   */
  private JsonNode loadOpenApiSchemas() throws IOException {
    Path path =
        OPENAPI_CANDIDATES.stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "openapi.json not found; looked at " + OPENAPI_CANDIDATES));
    JsonNode schemas =
        new ObjectMapper().readTree(path.toFile()).path("components").path("schemas");
    assertThat(schemas.isObject())
        .as("components.schemas in %s should be an object", path)
        .isTrue();
    return schemas;
  }

  /**
   * Collects the enum constant names from an openapi property's {@code enum} array.
   *
   * @param property the schema property node
   * @return the enum values as an insertion-ordered set
   */
  private Set<String> enumValueSet(JsonNode property) {
    Set<String> values = new LinkedHashSet<>();
    property.get("enum").forEach(node -> values.add(node.asText()));
    return values;
  }

  /**
   * Renders an openapi property's enum values for a violation message.
   *
   * @param property the schema property node
   * @return the enum values in {@code [A, B, C]} form
   */
  private String enumValues(JsonNode property) {
    return enumValueSet(property).toString();
  }
}
