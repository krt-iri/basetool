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

package de.greluc.krt.profit.basetool.ingest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ArchUnit guards for the ingest gateway's load-bearing invariants (REQ-INGEST-001/-002): the
 * module owns no database (no JPA entities, no JPA repositories), and every REST surface is
 * authorization-annotated. These mirror the relevant backend {@code ArchitectureTest} rules, scoped
 * to this module's package.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("de.greluc.krt.profit.basetool.ingest");

  /** The gateway has no database, so it must never touch JPA entity or repository APIs. */
  @Test
  void shouldNotDependOnJpaOrRelationalPersistence() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..")
        .as("the ingest gateway owns no database and must not use JPA")
        .check(CLASSES);
  }

  /** Every {@code @RestController} must carry an explicit authorization annotation. */
  @Test
  void everyRestControllerShouldBeAuthorisationAnnotated() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .as("every REST controller must declare @PreAuthorize at class level")
        .check(CLASSES);
  }

  /** Every write mapping must carry its own method-level authorization annotation. */
  @Test
  void everyPostMappingShouldBeAuthorisationAnnotated() {
    methods()
        .that()
        .areAnnotatedWith(PostMapping.class)
        .should()
        .beAnnotatedWith(PreAuthorize.class)
        .as("every @PostMapping endpoint must declare its own @PreAuthorize")
        .check(CLASSES);
  }
}
