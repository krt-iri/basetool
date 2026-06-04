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

package de.greluc.krt.iri.basetool.frontend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that enforce CLAUDE.md's "frontend never talks to PostgreSQL or Keycloak Admin API
 * directly" rule mechanically.
 *
 * <p>The frontend is supposed to be a thin Thymeleaf renderer that delegates every data access to
 * the backend via {@code BackendApiClient}; any drift towards "let me just open a JDBC connection
 * for this one widget" is exactly the kind of subtle architecture rot that's hard to spot in PR
 * review but trivial for a static check.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("de.greluc.krt.iri.basetool.frontend");

  @Test
  void frontendShouldNotDependOnSpringDataJpa() {
    // No code under `de.greluc.krt.iri.basetool.frontend.*` may reference any class
    // from `org.springframework.data.jpa..` — that includes JpaRepository,
    // EntityManager helpers, etc. The frontend module deliberately does not pull
    // the spring-boot-starter-data-jpa dependency, so this check is a belt-and-
    // suspenders guard against accidentally adding it on a hot fix.
    noClasses()
        .that()
        .resideInAPackage("de.greluc.krt.iri.basetool.frontend..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.data.jpa..")
        .because(
            "The frontend module is forbidden from talking to the database directly; "
                + "data access goes through BackendApiClient.")
        .check(CLASSES);
  }

  @Test
  void frontendShouldNotUseJdbcDirectly() {
    // Sibling rule to JpaRepository: the frontend must not open JDBC connections of
    // its own either. `java.sql.Connection`/`Statement`/`PreparedStatement` are
    // forbidden imports.
    noClasses()
        .that()
        .resideInAPackage("de.greluc.krt.iri.basetool.frontend..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.sql.Connection")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.sql.Statement")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.sql.PreparedStatement")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.sql.DriverManager")
        .because(
            "The frontend module has no business holding a JDBC connection; "
                + "all persistence goes through the backend module via BackendApiClient.")
        .check(CLASSES);
  }
}
