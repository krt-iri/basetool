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

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the result ordering of {@link JobOrderRepository#findAllActiveWithMaterials} — the query
 * behind {@code /api/v1/orders/lookup} that feeds the Auftrag (job-order) filter and the per-row
 * job-order selects of the warehouse (Lager) views. The pickers must rank orders the same way the
 * Auftragsverwaltung does (default {@code priority,asc}): most-important priority first, orders
 * without a priority last, with a stable {@code displayId DESC} tiebreaker.
 *
 * <p>Run against the real Postgres test container (Flyway-migrated schema), so the {@code NULLS
 * LAST} semantics and the DB-generated {@code display_id} sequence are exercised at production
 * parity. The Testcontainer is shared and other suites commit job-order rows, so the assertion
 * filters the result down to the ids created here and checks their <em>relative</em> order rather
 * than asserting an exact, suite-global list.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobOrderRepositoryActiveLookupOrderingTest {

  @Autowired private JobOrderRepository jobOrderRepository;
  @Autowired private SquadronRepository squadronRepository;

  /**
   * Priorities are persisted out of order (30, 10, 20) so the result cannot pass by coincidence of
   * insertion order, and two priority-less orders pin down the NULLS-LAST branch plus the {@code
   * displayId DESC} tiebreaker: each is saved-and-flushed in turn, so the later one gets the higher
   * generated {@code display_id} and must therefore sort ahead of the earlier one.
   */
  @Test
  void findAllActiveWithMaterials_ordersByPriorityAscNullsLastThenDisplayIdDesc() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    Squadron squadron = new Squadron();
    squadron.setName("Prio-Order-" + tag);
    squadron.setShorthand("PO" + tag.substring(0, 3));
    OrgUnit owner = squadronRepository.save(squadron);

    UUID prio30 = saveActiveOrder(owner, 30);
    UUID prio10 = saveActiveOrder(owner, 10);
    UUID prio20 = saveActiveOrder(owner, 20);
    UUID noPrioEarly = saveActiveOrder(owner, null);
    UUID noPrioLate = saveActiveOrder(owner, null);

    Set<UUID> mine = Set.of(prio30, prio10, prio20, noPrioEarly, noPrioLate);
    List<UUID> mineInResultOrder =
        jobOrderRepository.findAllActiveWithMaterials().stream()
            .map(JobOrder::getId)
            .filter(mine::contains)
            .toList();

    assertThat(mineInResultOrder).containsExactly(prio10, prio20, prio30, noPrioLate, noPrioEarly);
  }

  /**
   * Persists one {@code OPEN} (hence active) job order owned by {@code owner} on both org-unit FKs
   * and flushes immediately, so the DB assigns its {@code display_id} in call order.
   *
   * @param owner the responsible and requesting org unit (both {@code NOT NULL} FKs).
   * @param priority the manual priority rank, or {@code null} to exercise the NULLS-LAST branch.
   * @return the generated job-order id.
   */
  private UUID saveActiveOrder(OrgUnit owner, Integer priority) {
    JobOrder order =
        JobOrder.builder()
            .responsibleOrgUnit(owner)
            .requestingOrgUnit(owner)
            .handle("prio-order")
            .priority(priority)
            .status(JobOrderStatus.OPEN)
            .build();
    return jobOrderRepository.saveAndFlush(order).getId();
  }
}
