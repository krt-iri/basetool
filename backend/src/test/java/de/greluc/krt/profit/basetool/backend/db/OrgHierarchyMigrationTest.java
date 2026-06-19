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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.greluc.krt.profit.basetool.backend.repository.RoleRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies the V164 org-hierarchy migration (epic #692, REQ-ORG-014/017): the two new {@code
 * org_unit} kinds, the {@code parent_org_unit_id} column with its index, the cross-row parent-kind
 * trigger, the OL-has-no-parent CHECK, the relaxed "at most two Staffeln" guard (the old single-
 * Staffel unique index is gone, replaced by INSERT- and UPDATE-side counting triggers), and the new
 * {@code org_unit_membership} leadership flags. The test profile boots Postgres via Testcontainers
 * and runs every migration at startup, so this exercises the real DDL.
 *
 * <p>Three angles are covered: structural presence of every new object (so a renamed/dropped object
 * is caught as an early-warning canary), the org_unit-side three-level parent invariants, and the
 * membership-side ≤2-Staffel counting trigger on both INSERT and UPDATE (the UPDATE path proves the
 * re-point edge case where the about-to-be-replaced row must not be counted against itself). The
 * membership-flag CHECKs and the matching service-layer guard (REQ-ORG-017) are verified in a later
 * phase where the service and its fixtures exist; here throwaway {@code app_user} / {@code
 * org_unit} rows are inserted directly and removed in a finally block so the shared schema is left
 * untouched.
 */
@SpringBootTest
class OrgHierarchyMigrationTest {

  @MockitoBean private RoleRepository roleRepository;

  @MockitoBean private SquadronRepository squadronRepository;

  @Autowired private DataSource dataSource;

  /** Structural checks: the new column, index, dropped index, constraints and trigger all exist. */
  @Test
  void v164AddsHierarchyColumnsConstraintsAndDropsTheOneSquadronIndex() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    assertColumnExists(jdbc, "org_unit", "parent_org_unit_id");
    assertIndexExists(jdbc, "org_unit", "idx_org_unit_parent");

    // The leadership flags landed on org_unit_membership.
    assertColumnExists(jdbc, "org_unit_membership", "is_bereichsleiter");
    assertColumnExists(jdbc, "org_unit_membership", "is_bereichskoordinator");
    assertColumnExists(jdbc, "org_unit_membership", "is_bereichsoperator");
    assertColumnExists(jdbc, "org_unit_membership", "is_ol_member");

    // The "at most one Staffel" partial unique index is replaced by the ≤2 counting triggers,
    // which fire on BOTH INSERT and UPDATE to match the dropped index's full write coverage.
    assertIndexAbsent(jdbc, "uq_org_unit_membership_one_squadron");
    assertTriggerExists(
        jdbc, "org_unit_membership", "trg_org_unit_membership_max_two_squadron_ins");
    assertTriggerExists(
        jdbc, "org_unit_membership", "trg_org_unit_membership_max_two_squadron_upd");

    // New CHECK constraints and the parent-validation trigger are present.
    assertConstraintExists(jdbc, "chk_org_unit_ol_has_no_parent");
    assertConstraintExists(jdbc, "chk_org_unit_membership_bereich_flags_only_on_bereich");
    assertConstraintExists(jdbc, "chk_org_unit_membership_ol_flag_only_on_ol");
    assertTriggerExists(jdbc, "org_unit", "trg_org_unit_parent_ins");
  }

  /**
   * Behavioural checks on the org_unit side (no user fixtures needed): the new kinds are accepted,
   * the three-level parent chain (OL → Bereich → Staffel) is allowed, and every parent/promotion
   * invariant is rejected. Uses throwaway rows cleaned up in a finally block so the shared
   * Testcontainers schema is left untouched.
   */
  @Test
  void v164EnforcesTheThreeLevelParentHierarchy() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID olId = UUID.fromString("ffffff00-0000-0000-0000-0000000000a1");
    UUID bereichId = UUID.fromString("ffffff00-0000-0000-0000-0000000000a2");
    UUID squadronId = UUID.fromString("ffffff00-0000-0000-0000-0000000000a3");
    UUID rejectedId = UUID.fromString("ffffff00-0000-0000-0000-0000000000a4");

    cleanup(jdbc, squadronId, bereichId, olId, rejectedId);
    try {
      // Happy path: OL (no parent) -> Bereich (parent OL) -> Squadron (parent Bereich).
      insertOrgUnit(jdbc, olId, "ORGANISATIONSLEITUNG", "TEST_OH_OL", "TOHOL", false, null);
      insertOrgUnit(jdbc, bereichId, "BEREICH", "TEST_OH_BER", "TOHB", false, olId);
      insertOrgUnit(jdbc, squadronId, "SQUADRON", "TEST_OH_SQ", "TOHS", true, bereichId);

      assertThat(countById(jdbc, olId)).isOne();
      assertThat(countById(jdbc, bereichId)).isOne();
      assertThat(countById(jdbc, squadronId)).isOne();

      // A Bereich must have an OL parent — a Squadron parent is rejected by the parent trigger.
      // Asserting on the trigger's message (not just any DataAccessException) pins the failure to
      // the parent-kind rule, so a coincidental constraint violation cannot green this case.
      assertThatThrownBy(
              () ->
                  insertOrgUnit(
                      jdbc, rejectedId, "BEREICH", "TEST_OH_X", "TOHX", false, squadronId))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("must have an ORGANISATIONSLEITUNG parent");

      // An OL must have no parent — the BEFORE trigger fires before the CHECK, so its message wins.
      assertThatThrownBy(
              () ->
                  insertOrgUnit(
                      jdbc, rejectedId, "ORGANISATIONSLEITUNG", "TEST_OH_X", "TOHX", false, olId))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("must not have a parent");

      // A Bereich must never carry promotion — rejected by chk_org_unit_promotion_only_squadron.
      assertThatThrownBy(
              () -> insertOrgUnit(jdbc, rejectedId, "BEREICH", "TEST_OH_X", "TOHX", true, olId))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("chk_org_unit_promotion_only_squadron");
    } finally {
      cleanup(jdbc, squadronId, bereichId, olId, rejectedId);
    }
  }

  /**
   * Behavioural checks for the ≤2-Staffel counting trigger on both INSERT and UPDATE (REQ-ORG-017),
   * the guard that replaced the old single-Staffel unique index. Inserts a throwaway user plus four
   * org units (three Staffeln + one Spezialkommando), then drives the trigger: a third Staffel
   * INSERT is rejected, a re-point that keeps the user at two Staffeln is allowed (the
   * about-to-be-replaced row must not be counted against itself), and an UPDATE that would push the
   * user to a third Staffel is rejected. All rows are removed in a finally block.
   */
  @Test
  void v164EnforcesAtMostTwoSquadronMembershipsOnInsertAndUpdate() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID userId = UUID.fromString("ffffff00-0000-0000-0000-0000000000b0");
    UUID sqA = UUID.fromString("ffffff00-0000-0000-0000-0000000000b1");
    UUID sqB = UUID.fromString("ffffff00-0000-0000-0000-0000000000b2");
    UUID sqC = UUID.fromString("ffffff00-0000-0000-0000-0000000000b3");
    UUID skX = UUID.fromString("ffffff00-0000-0000-0000-0000000000b4");

    cleanupMemberships(jdbc, userId);
    cleanup(jdbc, sqA, sqB, sqC, skX);
    cleanupUser(jdbc, userId);
    try {
      insertUser(jdbc, userId);
      insertOrgUnit(jdbc, sqA, "SQUADRON", "TEST_OH_SQA", "TOSA", false, null);
      insertOrgUnit(jdbc, sqB, "SQUADRON", "TEST_OH_SQB", "TOSB", false, null);
      insertOrgUnit(jdbc, sqC, "SQUADRON", "TEST_OH_SQC", "TOSC", false, null);
      insertOrgUnit(jdbc, skX, "SPECIAL_COMMAND", "TEST_OH_SKX", "TOSX", false, null);

      // Two Staffeln are fine; the kind is filled in by the sync trigger from the org_unit row.
      insertMembership(jdbc, userId, sqA);
      insertMembership(jdbc, userId, sqB);
      assertThat(squadronMembershipCount(jdbc, userId)).isEqualTo(2);

      // A third Staffel INSERT trips the counting trigger.
      assertThatThrownBy(() -> insertMembership(jdbc, userId, sqC))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("at most two Staffeln");

      // An SK membership does not count towards the Staffel cap.
      insertMembership(jdbc, userId, skX);
      assertThat(squadronMembershipCount(jdbc, userId)).isEqualTo(2);

      // Re-pointing one of the two Staffeln to another Staffel keeps the user at two — allowed.
      // Without OLD-row exclusion in the UPDATE trigger the replaced sqA row would miscount as a
      // third Staffel and this valid move would be wrongly rejected.
      repointMembership(jdbc, userId, sqA, sqC);
      assertThat(membershipExists(jdbc, userId, sqC)).isTrue();
      assertThat(membershipExists(jdbc, userId, sqA)).isFalse();
      assertThat(squadronMembershipCount(jdbc, userId)).isEqualTo(2);

      // Re-pointing the SK membership onto a Staffel would make a third Staffel — rejected.
      assertThatThrownBy(() -> repointMembership(jdbc, userId, skX, sqA))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("at most two Staffeln");
    } finally {
      cleanupMemberships(jdbc, userId);
      cleanup(jdbc, sqA, sqB, sqC, skX);
      cleanupUser(jdbc, userId);
    }
  }

  /**
   * V165 (REQ-ORG-017): the cross-row "a leader holds no Staffel" invariant is enforced by a
   * trigger on {@code org_unit_membership} firing on both INSERT and UPDATE. The behavioural
   * rejection paths are covered through the service layer (where user fixtures exist); here the
   * trigger's presence is the early-warning canary.
   */
  @Test
  void v165AddsLeaderExcludesSquadronTrigger() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertTriggerExists(
        jdbc, "org_unit_membership", "trg_org_unit_membership_leader_excl_squadron_ins");
    assertTriggerExists(
        jdbc, "org_unit_membership", "trg_org_unit_membership_leader_excl_squadron_upd");
  }

  private static void insertOrgUnit(
      JdbcTemplate jdbc,
      UUID id,
      String kind,
      String name,
      String shorthand,
      boolean promotionEnabled,
      UUID parentId) {
    jdbc.update(
        "INSERT INTO org_unit (id, kind, name, shorthand, active, is_promotion_enabled,"
            + " is_profit_eligible, parent_org_unit_id) VALUES (?, ?, ?, ?, TRUE, ?, FALSE, ?)",
        id,
        kind,
        name,
        shorthand,
        promotionEnabled,
        parentId);
  }

  private static int countById(JdbcTemplate jdbc, UUID id) {
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM org_unit WHERE id = ?", Integer.class, id);
    return count == null ? 0 : count;
  }

  /** Deletes the throwaway rows child-first so the self-referential FK never blocks the cleanup. */
  private static void cleanup(JdbcTemplate jdbc, UUID... idsChildFirst) {
    for (UUID id : idsChildFirst) {
      jdbc.update("DELETE FROM org_unit WHERE id = ?", id);
    }
  }

  /** Inserts a minimal throwaway user; only {@code id} is required (other columns default). */
  private static void insertUser(JdbcTemplate jdbc, UUID id) {
    jdbc.update("INSERT INTO app_user (id) VALUES (?)", id);
  }

  /**
   * Inserts a membership; {@code kind} is denormalised from the referenced {@code org_unit} by the
   * V98 sync trigger and the leadership flags default to {@code false}, so neither is set here.
   */
  private static void insertMembership(JdbcTemplate jdbc, UUID userId, UUID orgUnitId) {
    jdbc.update(
        "INSERT INTO org_unit_membership (user_id, org_unit_id) VALUES (?, ?)", userId, orgUnitId);
  }

  /** Re-points a membership to a different org unit, exercising the UPDATE counting trigger. */
  private static void repointMembership(
      JdbcTemplate jdbc, UUID userId, UUID fromOrgUnitId, UUID toOrgUnitId) {
    jdbc.update(
        "UPDATE org_unit_membership SET org_unit_id = ? WHERE user_id = ? AND org_unit_id = ?",
        toOrgUnitId,
        userId,
        fromOrgUnitId);
  }

  private static boolean membershipExists(JdbcTemplate jdbc, UUID userId, UUID orgUnitId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM org_unit_membership WHERE user_id = ? AND org_unit_id = ?",
            Integer.class,
            userId,
            orgUnitId);
    return count != null && count > 0;
  }

  private static int squadronMembershipCount(JdbcTemplate jdbc, UUID userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM org_unit_membership WHERE user_id = ? AND kind = 'SQUADRON'",
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  /** Removes every membership of the throwaway user before its org units / row are deleted. */
  private static void cleanupMemberships(JdbcTemplate jdbc, UUID userId) {
    jdbc.update("DELETE FROM org_unit_membership WHERE user_id = ?", userId);
  }

  /** Removes the throwaway user row (memberships must be gone first). */
  private static void cleanupUser(JdbcTemplate jdbc, UUID id) {
    jdbc.update("DELETE FROM app_user WHERE id = ?", id);
  }

  private static void assertColumnExists(JdbcTemplate jdbc, String table, String column) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_name = ? AND column_name = ?",
            String.class,
            table,
            column);
    assertThat(rows).as("Expected column %s.%s to exist", table, column).isNotEmpty();
  }

  private static void assertIndexExists(JdbcTemplate jdbc, String table, String indexName) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = ? AND indexname = ?",
            String.class,
            table,
            indexName);
    assertThat(rows).as("Expected index %s on table %s", indexName, table).isNotEmpty();
  }

  private static void assertIndexAbsent(JdbcTemplate jdbc, String indexName) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE indexname = ?", String.class, indexName);
    assertThat(rows).as("Expected index %s to have been dropped by V164", indexName).isEmpty();
  }

  private static void assertConstraintExists(JdbcTemplate jdbc, String constraintName) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT conname FROM pg_constraint WHERE conname = ?", String.class, constraintName);
    assertThat(rows).as("Expected constraint %s to exist", constraintName).isNotEmpty();
  }

  private static void assertTriggerExists(JdbcTemplate jdbc, String table, String triggerName) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT t.tgname FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid"
                + " WHERE c.relname = ? AND t.tgname = ?",
            String.class,
            table,
            triggerName);
    assertThat(rows).as("Expected trigger %s on table %s", triggerName, table).isNotEmpty();
  }
}
