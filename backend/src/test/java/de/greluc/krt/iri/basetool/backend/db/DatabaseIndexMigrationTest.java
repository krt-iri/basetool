package de.greluc.krt.iri.basetool.backend.db;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that the indexes introduced by the Flyway migrations actually exist in the test
 * database. The test profile now boots Postgres via Testcontainers and runs every V<n>__*.sql
 * migration during context startup (see {@code application-test.yml}), so this test no longer needs
 * its own container/dynamic-property wiring nor the {@code ENABLE_TC} gate that historically kept
 * it from running in the default build — it is part of the standard test suite now.
 */
@SpringBootTest
class DatabaseIndexMigrationTest {

  @MockitoBean private RoleRepository roleRepository;

  @MockitoBean private SquadronRepository squadronRepository;

  @Autowired private DataSource dataSource;

  /**
   * Spot-checks a handful of indexes from the Flyway migrations to make sure they are actually
   * present in the live Postgres test schema. Picks one representative index from each migration
   * that introduces a non-trivial indexing strategy:
   *
   * <ul>
   *   <li>V34 (foreign-key b-tree index, e.g. {@code idx_ship_owner_id})
   *   <li>V35 (pg_trgm GIN index used by the ILIKE search endpoints)
   *   <li>V48 (mission owner/manager indexes added with the ownership rewrite)
   *   <li>V65 (personal inventory composite owner+name index)
   *   <li>V92 (backfill FK indexes that escaped V34's blanket sweep)
   *   <li>V122 (second FK backfill: handover / unit / yield lookup indexes)
   * </ul>
   *
   * The test is intentionally not exhaustive: it acts as an early-warning canary that Flyway
   * actually ran and produced the expected DDL. A missing index here is almost always a sign that a
   * migration was renamed/squashed without updating the index name.
   */
  @Test
  void flywayMigrationAddsExpectedIndexes() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    // V34: FK index on ship.owner_id
    assertIndexExists(jdbc, "ship", "idx_ship_owner_id");
    // V34: FK index on inventory_item.material_id
    assertIndexExists(jdbc, "inventory_item", "idx_inventory_item_material_id");
    // V35: trigram GIN index on mission.name (powers the ILIKE search)
    assertIndexExists(jdbc, "mission", "idx_mission_name_trgm");
    // V35: trigram GIN index on material.name
    assertIndexExists(jdbc, "material", "idx_material_name_trgm");
    // V48: mission owner index
    assertIndexExists(jdbc, "mission", "idx_mission_owner");
    // V65: composite owner+name index on personal_inventory_item
    assertIndexExists(jdbc, "personal_inventory_item", "idx_personal_inventory_item_owner_name");
    // V92: FK index on mission.operation_id (powers the operation filter on the missions list)
    assertIndexExists(jdbc, "mission", "idx_mission_operation_id");
    // V92: FK index on inventory_item.job_order_id (powers the job order detail page)
    assertIndexExists(jdbc, "inventory_item", "idx_inventory_item_job_order_id");
    // V96: partial UNIQUE index that backs the multi-user signup race fix — keeps a registered
    // user from being added twice to the same mission via two parallel "Anmelden" clicks. The
    // in-memory check in MissionService.addParticipant catches the common case; this index is
    // the DB-level backstop against the TOCTOU race.
    assertIndexExists(jdbc, "mission_participant", "uq_mission_participant_user");
    // V122: FK index on job_order_handover.job_order_id (powers the handover view) and the
    // ON DELETE CASCADE child index on job_order_handover_item — representative of the second
    // FK backfill sweep.
    assertIndexExists(jdbc, "job_order_handover", "idx_job_order_handover_job_order_id");
    assertIndexExists(jdbc, "job_order_handover_item", "idx_job_order_handover_item_handover_id");
    // V122: composite (terminal_id, material_id) lookup index on refinery_yield.
    assertIndexExists(jdbc, "refinery_yield", "idx_refinery_yield_terminal_material");
  }

  private static void assertIndexExists(JdbcTemplate jdbc, String table, String indexName) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = ? AND indexname = ?",
            String.class,
            table,
            indexName);
    assertThat(rows)
        .as("Expected Flyway-managed index %s on table %s to be present", indexName, table)
        .isNotEmpty();
  }
}
