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
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies the V169 bank-backfill matching logic (epic #692, REQ-ORG-019 / REQ-BANK-027): a legacy
 * {@code area_name}-only {@code AREA} account is linked to the Bereich whose name equals its {@code
 * area_name} (case-insensitive, trimmed) and its {@code area_name} is cleared in the same
 * statement, while an {@code AREA} account whose {@code area_name} matches no Bereich is left
 * untouched. The boot-time migration is a no-op on the test seed (no matching legacy data), so this
 * test inserts throwaway {@code org_unit} / {@code bank_account} rows after startup and replays the
 * migration's exact AREA {@code UPDATE} to assert the name-match + {@code
 * chk_bank_account_owner_ref}-safe area-name clearing. The CARTEL→OL link is the trivial singleton
 * case (guarded by a one-OL count) and is not exercised here because inserting a throwaway {@code
 * CARTEL} would collide with the {@code uq_bank_account_singleton_cartel} index.
 *
 * <p>All rows are removed in a finally block so the shared schema is left untouched.
 */
@SpringBootTest
class V169BankBackfillMigrationTest {

  /** The AREA backfill statement from {@code V169}, replayed verbatim against throwaway rows. */
  private static final String AREA_BACKFILL_SQL =
      """
      UPDATE bank_account ba
      SET org_unit_id = (
              SELECT ou.id FROM org_unit ou
              WHERE ou.kind = 'BEREICH'
                AND lower(btrim(ou.name)) = lower(btrim(ba.area_name))
          ),
          area_name = NULL
      WHERE ba.type = 'AREA'
        AND ba.org_unit_id IS NULL
        AND ba.area_name IS NOT NULL
        AND (
              SELECT count(*) FROM org_unit ou
              WHERE ou.kind = 'BEREICH'
                AND lower(btrim(ou.name)) = lower(btrim(ba.area_name))
            ) = 1
      """;

  @MockitoBean private RoleRepository roleRepository;
  @MockitoBean private SquadronRepository squadronRepository;

  @Autowired private DataSource dataSource;

  @Test
  void v169LinksLegacyAreaToBereichByNameAndLeavesNonMatchUntouched() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID bereichId = UUID.fromString("ffffff69-0000-0000-0000-0000000000a1");
    UUID matchAcctId = UUID.fromString("ffffff69-0000-0000-0000-0000000000b1");
    UUID noMatchAcctId = UUID.fromString("ffffff69-0000-0000-0000-0000000000b2");

    cleanupAccounts(jdbc, matchAcctId, noMatchAcctId);
    cleanup(jdbc, bereichId);
    try {
      // A Bereich named "TEST_V169_PROFIT"; the legacy AREA account names the area in a different
      // case ("test_v169_profit") to prove the match is case-insensitive and trimmed.
      insertOrgUnit(jdbc, bereichId, "BEREICH", "TEST_V169_PROFIT", "TV9B");
      insertAccount(
          jdbc, matchAcctId, "KB-V169-1", "Bereich Profit", "AREA", null, "test_v169_profit");
      insertAccount(
          jdbc, noMatchAcctId, "KB-V169-2", "Bereich Other", "AREA", null, "TEST_V169_NOMATCH");

      int updated = jdbc.update(AREA_BACKFILL_SQL);

      // Exactly the one matching account is linked.
      assertThat(updated).isOne();
      // Matching account: FK now points at the Bereich and area_name is cleared (CHECK-safe).
      assertThat(orgUnitIdOf(jdbc, matchAcctId)).isEqualTo(bereichId);
      assertThat(areaNameOf(jdbc, matchAcctId)).isNull();
      // Non-matching account: untouched (still legacy area_name, no FK).
      assertThat(orgUnitIdOf(jdbc, noMatchAcctId)).isNull();
      assertThat(areaNameOf(jdbc, noMatchAcctId)).isEqualTo("TEST_V169_NOMATCH");

      // Idempotency: a second run links nothing more.
      assertThat(jdbc.update(AREA_BACKFILL_SQL)).isZero();
    } finally {
      cleanupAccounts(jdbc, matchAcctId, noMatchAcctId);
      cleanup(jdbc, bereichId);
    }
  }

  private static void insertOrgUnit(
      JdbcTemplate jdbc, UUID id, String kind, String name, String shorthand) {
    jdbc.update(
        "INSERT INTO org_unit (id, kind, name, shorthand, active, is_promotion_enabled,"
            + " is_profit_eligible, parent_org_unit_id) VALUES (?, ?, ?, ?, TRUE, FALSE, FALSE,"
            + " NULL)",
        id,
        kind,
        name,
        shorthand);
  }

  private static void insertAccount(
      JdbcTemplate jdbc,
      UUID id,
      String accountNo,
      String name,
      String type,
      UUID orgUnitId,
      String areaName) {
    jdbc.update(
        "INSERT INTO bank_account (id, account_no, name, type, status, org_unit_id, area_name)"
            + " VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
        id,
        accountNo,
        name,
        type,
        orgUnitId,
        areaName);
  }

  private static UUID orgUnitIdOf(JdbcTemplate jdbc, UUID accountId) {
    return jdbc.queryForObject(
        "SELECT org_unit_id FROM bank_account WHERE id = ?", UUID.class, accountId);
  }

  private static String areaNameOf(JdbcTemplate jdbc, UUID accountId) {
    return jdbc.queryForObject(
        "SELECT area_name FROM bank_account WHERE id = ?", String.class, accountId);
  }

  private static void cleanupAccounts(JdbcTemplate jdbc, UUID... ids) {
    for (UUID id : ids) {
      jdbc.update("DELETE FROM bank_account WHERE id = ?", id);
    }
  }

  private static void cleanup(JdbcTemplate jdbc, UUID... ids) {
    for (UUID id : ids) {
      jdbc.update("DELETE FROM org_unit WHERE id = ?", id);
    }
  }
}
