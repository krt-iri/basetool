package de.greluc.krt.iri.basetool.backend.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialSourceSystem;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestContainers-backed migration test for {@code
 * V116__update_material_source_systems_for_is_manual_entry.sql}. The migration is a one-shot data
 * backfill (no schema change), so the test pins (1) that Flyway recorded it as applied, (2) the
 * post-migration invariant — no {@code is_manual_entry = TRUE} row keeps a non-{@code MANUAL}
 * {@code source_systems} — and (3) the backfill SQL itself, by re-running it against controlled
 * rows to prove it flips a manual row, leaves a non-manual row alone, and is a no-op on an
 * already-{@code MANUAL} row.
 *
 * <p>{@code @Transactional} rolls back the controlled rows so they never pollute the shared test
 * database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class V116MigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MaterialRepository materialRepository;

  @Test
  void v116IsRecordedAsApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '116' AND success = true",
            Integer.class);
    assertEquals(1, applied == null ? 0 : applied, "V116 must be applied successfully");
  }

  @Test
  void v116Invariant_noManualRowKeepsANonManualSourceSystem() {
    Integer violations =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM material "
                + "WHERE is_manual_entry = TRUE AND source_systems <> 'MANUAL'",
            Integer.class);
    assertEquals(
        0,
        violations == null ? -1 : violations,
        "after V116, no manual-entry material may keep a non-MANUAL source_systems");
  }

  @Test
  void backfillSql_flipsManualRows_andLeavesOthersUntouched() {
    UUID manualUexOnly = saveMaterial(true, MaterialSourceSystem.UEX_ONLY);
    UUID notManual = saveMaterial(false, MaterialSourceSystem.UEX_ONLY);
    UUID manualAlready = saveMaterial(true, MaterialSourceSystem.MANUAL);

    // The Flyway migration already ran at context start-up; re-run the exact backfill statement to
    // assert the flip logic directly against controlled rows.
    jdbcTemplate.update(
        "UPDATE material SET source_systems = 'MANUAL' "
            + "WHERE is_manual_entry = TRUE AND source_systems <> 'MANUAL'");

    assertEquals(
        "MANUAL", sourceSystemsOf(manualUexOnly), "manual UEX_ONLY row must flip to MANUAL");
    assertEquals("UEX_ONLY", sourceSystemsOf(notManual), "non-manual row must stay UEX_ONLY");
    assertEquals("MANUAL", sourceSystemsOf(manualAlready), "already-MANUAL row stays MANUAL");
  }

  private String sourceSystemsOf(UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT source_systems FROM material WHERE id = ?", String.class, id);
  }

  /**
   * Persists a minimal valid {@link Material} via the repository (so every NOT NULL column gets its
   * entity default) with the {@code is_manual_entry} / {@code source_systems} combination under
   * test.
   *
   * @param manual value for {@code is_manual_entry}
   * @param sourceSystems initial source-systems provenance
   * @return the generated row id
   */
  private UUID saveMaterial(boolean manual, MaterialSourceSystem sourceSystems) {
    Material material = new Material();
    material.setName("R116 test " + UUID.randomUUID());
    material.setType(MaterialType.NO_REFINE);
    material.setIsManualEntry(manual);
    material.setSourceSystems(sourceSystems);
    return materialRepository.saveAndFlush(material).getId();
  }
}
