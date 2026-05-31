package de.greluc.krt.iri.basetool.backend.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * TestContainers-backed regression tests for {@link BlueprintRepository}'s admin-page list queries.
 * These run the real SQL against PostgreSQL so the empty-search path can never silently regress to
 * the {@code function lower(bytea) does not exist} failure that broke {@code GET
 * /api/v1/blueprints} whenever the admin opened the page without a filter: a {@code null} named
 * parameter inlined into {@code LOWER(CONCAT(...))} makes PostgreSQL type the bind as {@code
 * bytea}. The queries are split so the no-filter load ({@link
 * BlueprintRepository#findByScwikiDeletedAtIsNull(Pageable)}) never passes a string-function
 * argument and the search load ({@link BlueprintRepository#searchActive(String, Pageable)}) only
 * ever receives a non-null term. An empty table is sufficient: PostgreSQL resolves every function
 * signature at plan time, so a grammar regression throws before any row is read.
 */
@SpringBootTest
@ActiveProfiles("test")
class BlueprintRepositoryTest {

  @Autowired private BlueprintRepository blueprintRepository;

  @Test
  void findByScwikiDeletedAtIsNull_executesAgainstPostgresWithoutSearchTerm() {
    Page<Blueprint> page = blueprintRepository.findByScwikiDeletedAtIsNull(PageRequest.of(0, 25));
    assertNotNull(page);
  }

  @Test
  void searchActive_executesAgainstPostgresWithSearchTerm() {
    Page<Blueprint> page = blueprintRepository.searchActive("omni", PageRequest.of(0, 25));
    assertNotNull(page);
  }
}
