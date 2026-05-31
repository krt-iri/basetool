package de.greluc.krt.iri.basetool.backend.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * TestContainers-backed migration test for {@code
 * V122__drop_legacy_material_and_ship_type_columns.sql} — the R9 Step 4 destructive drop. Pins that
 * Flyway recorded the migration and that both legacy columns ({@code material.is_manual_entry} and
 * {@code ship_type.description}) are gone from the fully-migrated schema. Because the matching JPA
 * fields were removed in the same change, a green {@code @SpringBootTest} context boot here also
 * proves {@code ddl-auto=validate} accepts the post-drop schema.
 */
@SpringBootTest
@ActiveProfiles("test")
class V122MigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void v122IsRecordedAsApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '122' AND success = true",
            Integer.class);
    assertEquals(1, applied == null ? 0 : applied, "V122 must be applied successfully");
  }

  @Test
  void v122DropsMaterialIsManualEntry() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'material' AND column_name = 'is_manual_entry'",
            Integer.class);
    assertEquals(0, count == null ? -1 : count, "material.is_manual_entry must be dropped by V122");
  }

  @Test
  void v122DropsShipTypeDescription() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'ship_type' AND column_name = 'description'",
            Integer.class);
    assertEquals(0, count == null ? -1 : count, "ship_type.description must be dropped by V122");
  }
}
