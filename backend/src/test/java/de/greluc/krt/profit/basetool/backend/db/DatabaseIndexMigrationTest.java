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

package de.greluc.krt.profit.basetool.backend.db;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.profit.basetool.backend.repository.RoleRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
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
    // V143: composite stack-key index backing the group-on-read GROUP BY and the lazy per-stack
    // entries lookup (ADR-0003, REQ-INV-002).
    assertIndexExists(jdbc, "inventory_item", "idx_inventory_item_stack_key");
    // V146 (covers REQ-REFINERY-010): case-insensitive unique index that replaced the V108
    // case-sensitive constraint — guarantees the IgnoreCase alias resolver can never see two
    // case-variant rows for the same (source_system, external_name).
    assertIndexExists(
        jdbc, "material_external_alias", "uq_material_external_alias_source_lower_name");
    // V150 (REQ-BANK-001): partial unique indexes enforcing one account per org unit and the
    // CARTEL / CARTEL_BANK singletons at the database level.
    assertIndexExists(jdbc, "bank_account", "uq_bank_account_org_unit");
    assertIndexExists(jdbc, "bank_account", "uq_bank_account_singleton_cartel");
    assertIndexExists(jdbc, "bank_account", "uq_bank_account_singleton_cartel_bank");
    // V152 (REQ-BANK-009): reverse lookup powering the per-account grants matrix.
    assertIndexExists(jdbc, "bank_account_grant", "idx_bank_account_grant_account");
    // V153 (REQ-BANK-020): the compute-on-read balance, statement and holder-distribution paths.
    assertIndexExists(jdbc, "bank_posting", "idx_bank_posting_account_created");
    assertIndexExists(jdbc, "bank_posting", "idx_bank_posting_transaction");
    assertIndexExists(jdbc, "bank_posting", "idx_bank_posting_account_holder");
    // V154 (REQ-BANK-012): the admin audit viewer (newest-first plus per-account filter).
    assertIndexExists(jdbc, "bank_audit_event", "idx_bank_audit_event_occurred");
    assertIndexExists(jdbc, "bank_audit_event", "idx_bank_audit_event_account");
    // V162 (REQ-DATA-004 / ADR-0023): the UEX company-id → manufacturer alias lookup index.
    assertIndexExists(
        jdbc, "manufacturer_uex_company", "idx_manufacturer_uex_company_manufacturer");
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
