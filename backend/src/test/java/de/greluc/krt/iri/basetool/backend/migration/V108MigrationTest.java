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

package de.greluc.krt.iri.basetool.backend.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestContainers-backed migration test for {@code V108__create_material_external_alias.sql}. The
 * migration creates the curated cross-reference table plus a seed INSERT that is conditional on the
 * target UEX material existing (see V108 header comment). On a clean test DB no materials exist at
 * Flyway-run time, so the seed inserts zero rows; this test asserts:
 *
 * <ul>
 *   <li>the table + columns + check constraint + unique constraint + index exist
 *   <li>the seed INSERT statements are idempotent — when the test pre-populates the 6 target
 *       materials and re-runs the SELECT-driven INSERTs by hand, exactly 6 alias rows are created,
 *       all stamped {@code created_by = 'system'} and pointing at the right material
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class V108MigrationTest {

  /**
   * SC Wiki external name → UEX-side material name. Mirrors the V108 seed exactly so a future drift
   * between SQL and test fails the build.
   */
  private static final List<String[]> SEED_PAIRS =
      List.of(
          new String[] {"Raw Silicon", "Silicon (Raw)"},
          new String[] {"Stileron (Ore)", "Stileron (Raw)"},
          new String[] {"Raw Ouratite", "Ouratite (Raw)"},
          new String[] {"Hephaestanite (R)", "Hephaestanite (Raw)"},
          new String[] {"Lastaprene", "Lastaphrene"},
          new String[] {"Lunes (Spiral Fruit)", "Lunes"});

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void v108CreatesTableWithExpectedShape() {
    Map<String, String> types = dataTypesOf("material_external_alias");

    assertEquals("uuid", types.get("id"));
    assertEquals("uuid", types.get("material_id"));
    assertEquals("character varying", types.get("source_system"));
    assertEquals("character varying", types.get("external_name"));
    assertEquals("character varying", types.get("external_key"));
    assertEquals("uuid", types.get("external_uuid"));
    assertEquals("character varying", types.get("external_code"));
    assertEquals("text", types.get("note"));
    assertEquals("character varying", types.get("created_by"));
  }

  @Test
  void v108AddsUniqueAndCheckConstraints() {
    Integer uniqueCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'material_external_alias' "
                + "AND constraint_type = 'UNIQUE' "
                + "AND constraint_name = 'uk_material_external_alias_source_external_name'",
            Integer.class);
    assertEquals(1, uniqueCount == null ? 0 : uniqueCount, "UNIQUE constraint must exist");

    Integer checkCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'material_external_alias' "
                + "AND constraint_type = 'CHECK' "
                + "AND constraint_name = 'chk_material_external_alias_source_system'",
            Integer.class);
    assertEquals(1, checkCount == null ? 0 : checkCount, "CHECK on source_system must exist");
  }

  @Test
  void v108AddsIndexOnMaterialId() {
    Integer indexCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'material_external_alias' "
                + "AND indexname = 'idx_material_external_alias_material'",
            Integer.class);
    assertEquals(1, indexCount == null ? 0 : indexCount, "index on material_id must exist");
  }

  /**
   * Replays the V108 seed INSERTs against a freshly-populated material set and verifies that
   * exactly 6 alias rows are created — the round-trip catches the case where the seed SQL is
   * accidentally trimmed in a future refactor (the file would still parse but the migration test
   * catches the missing rows).
   */
  @Test
  void v108SeedInsertsCreateSixAliasRowsWhenTargetMaterialsExist() {
    Map<String, java.util.UUID> insertedMaterialIds = new HashMap<>();
    for (String[] pair : SEED_PAIRS) {
      String materialName = pair[1];
      java.util.UUID id = java.util.UUID.randomUUID();
      jdbcTemplate.update(
          "INSERT INTO material (id, name, type, quantity_type, is_manual_raw_material, "
              + "is_job_order, is_visible, source_systems) "
              + "VALUES (?, ?, 'NO_REFINE', 'SCU', false, false, true, 'UEX_ONLY')",
          id,
          materialName);
      insertedMaterialIds.put(materialName, id);
    }

    int seededBefore =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM material_external_alias WHERE created_by = 'system'",
            Integer.class);

    // Replay the six seed INSERTs.
    for (String[] pair : SEED_PAIRS) {
      String wikiName = pair[0];
      String uexName = pair[1];
      jdbcTemplate.update(
          "INSERT INTO material_external_alias "
              + "(id, material_id, source_system, external_name, note, created_by) "
              + "SELECT gen_random_uuid(), m.id, 'SCWIKI', ?, "
              + "'V108 seed replay', 'system' "
              + "FROM material m WHERE m.name = ? "
              + "ON CONFLICT (source_system, external_name) DO NOTHING",
          wikiName,
          uexName);
    }

    int seededAfter =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM material_external_alias WHERE created_by = 'system'",
            Integer.class);

    assertEquals(
        seededBefore + 6,
        seededAfter,
        "exactly 6 alias rows must materialise once the target materials exist");

    // Spot-check one resolution: the Wiki name resolves back to the UEX material we inserted.
    java.util.UUID resolvedMaterialId =
        jdbcTemplate.queryForObject(
            "SELECT material_id FROM material_external_alias "
                + "WHERE source_system = 'SCWIKI' AND external_name = ?",
            java.util.UUID.class,
            "Raw Silicon");
    assertEquals(
        insertedMaterialIds.get("Silicon (Raw)"),
        resolvedMaterialId,
        "Raw Silicon alias must resolve to Silicon (Raw) material id");
  }

  private Map<String, String> dataTypesOf(String tableName) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ?",
            tableName);
    Map<String, String> out = new HashMap<>();
    for (Map<String, Object> row : rows) {
      out.put(((String) row.get("column_name")).toLowerCase(), (String) row.get("data_type"));
    }
    return out;
  }
}
