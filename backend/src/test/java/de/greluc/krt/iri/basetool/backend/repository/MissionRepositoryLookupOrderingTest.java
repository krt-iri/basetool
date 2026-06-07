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

package de.greluc.krt.iri.basetool.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the result ordering of {@link MissionRepository#findAllActiveReference} — the query that
 * feeds the Einsatz (mission) filter and per-item association select of the warehouse (Lager)
 * views. The picker must show the newest missions first, so the clause is {@code ORDER BY
 * plannedStartTime DESC NULLS LAST, name ASC}.
 *
 * <p>Run against the real Postgres test container (Flyway-migrated schema), so the {@code NULLS
 * LAST} semantics are validated at production parity rather than against an H2 default that
 * diverges from Postgres. The Testcontainer is shared and other suites commit mission rows, so the
 * assertion filters the result down to the ids created here and checks their <em>relative</em>
 * order rather than asserting an exact, suite-global list.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionRepositoryLookupOrderingTest {

  /** Far-past cut-off; irrelevant for the {@code ACTIVE} fixtures (only terminal missions gate). */
  private static final Instant CUTOFF = Instant.parse("2020-01-01T00:00:00Z");

  @Autowired private MissionRepository missionRepository;
  @Autowired private SquadronRepository squadronRepository;

  /**
   * The three dated fixtures are deliberately named in the opposite order to their planned start
   * (newest = "Zulu", oldest = "Alpha"), so a result that came back sorted by name would visibly
   * differ from one sorted by planned start — proving the ordering is by time, not name. The
   * undated mission must trail all dated ones (NULLS LAST) regardless of its name.
   */
  @Test
  void findAllActiveReference_ordersNewestPlannedStartFirstThenNullsLast() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    Squadron squadron = new Squadron();
    squadron.setName("Lookup-Order-" + tag);
    squadron.setShorthand("LO" + tag.substring(0, 3));
    OrgUnit owner = squadronRepository.save(squadron);

    UUID newestId = saveMission(owner, "Zulu", Instant.parse("2026-03-01T00:00:00Z"));
    UUID middleId = saveMission(owner, "Mike", Instant.parse("2026-02-01T00:00:00Z"));
    UUID oldestId = saveMission(owner, "Alpha", Instant.parse("2026-01-01T00:00:00Z"));
    UUID undatedId = saveMission(owner, "Bravo", null);

    Set<UUID> mine = Set.of(newestId, middleId, oldestId, undatedId);
    List<MissionReferenceDto> result =
        missionRepository.findAllActiveReference(true, null, Set.of(), true, CUTOFF);

    List<UUID> mineInResultOrder =
        result.stream().map(MissionReferenceDto::id).filter(mine::contains).toList();

    assertThat(mineInResultOrder).containsExactly(newestId, middleId, oldestId, undatedId);
  }

  /**
   * Persists one {@code ACTIVE} mission so it is always returned by the lookup regardless of the
   * three-month terminal cut-off.
   *
   * @param owner the owning org unit (a {@code NOT NULL} FK on the mission row).
   * @param name the mission display name used by the {@code name} tiebreaker.
   * @param plannedStartTime the planned start, or {@code null} to exercise the NULLS-LAST branch.
   * @return the generated mission id.
   */
  private UUID saveMission(OrgUnit owner, String name, Instant plannedStartTime) {
    Mission mission = new Mission();
    mission.setName(name);
    mission.setStatus("ACTIVE");
    mission.setIsInternal(false);
    mission.setOwningOrgUnit(owner);
    mission.setPlannedStartTime(plannedStartTime);
    return missionRepository.save(mission).getId();
  }
}
