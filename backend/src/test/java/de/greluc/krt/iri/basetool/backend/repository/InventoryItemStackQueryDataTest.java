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

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.projection.InventoryStackAggregate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Data-level regression coverage for the group-on-read stack queries ({@link
 * InventoryItemRepository#findGlobalStacks} / {@link InventoryItemRepository#findUserStacks},
 * ADR-0003, REQ-INV-002) against the real Postgres test schema (Testcontainers + Flyway via the
 * {@code test} profile).
 *
 * <p>The sibling {@code InventoryItemStackQueryTest} only smoke-tests these queries against an empty
 * table, so it cannot catch the trap this test pins down: the projection selects and groups the
 * <em>nullable</em> associations {@code jobOrder}, {@code mission} and {@code owningOrgUnit} as whole
 * entities. A naive constructor-expression projection over a nullable to-one renders an implicit
 * INNER JOIN, which silently drops every row where that association is {@code null} — i.e. the vast
 * majority of real Lager stock (most items belong to no job order and no mission). That made {@code
 * /inventory/all} and {@code /inventory/my} show "no entries" even though the aggregated overview
 * listed the very same material. These tests seed exactly such rows and assert they still surface.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryItemStackQueryDataTest {

  @Autowired private InventoryItemRepository inventoryItemRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MaterialRepository materialRepository;
  @Autowired private LocationRepository locationRepository;
  @Autowired private SquadronRepository squadronRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  /**
   * A squadron-owned, non-personal item that is linked to neither a job order nor a mission (the
   * common case) must surface in the global stack view. Before the implicit-join fix the
   * constructor-expression INNER JOIN on {@code jobOrder} / {@code mission} dropped it, so the
   * admin-wide Lager came back empty.
   */
  @Test
  void findGlobalStacks_includesNonPersonalItemWithoutJobOrderOrMission() {
    UUID materialId =
        transactionTemplate.execute(
            status -> {
              User user = new User();
              user.setId(UUID.randomUUID());
              user.setUsername("u-" + UUID.randomUUID());
              userRepository.save(user);

              Location location = new Location();
              location.setName("Hub-" + UUID.randomUUID());
              location = locationRepository.save(location);

              Material material = new Material();
              material.setName("Quantanium-" + UUID.randomUUID());
              material.setType(MaterialType.RAW);
              material = materialRepository.save(material);

              InventoryItem inv = new InventoryItem();
              inv.setUser(user);
              inv.setLocation(location);
              inv.setMaterial(material);
              inv.setQuality(800);
              inv.setAmount(100.0);
              inv.setPersonal(false);
              inv.setJobOrder(null);
              inv.setMission(null);
              inv.setOwningOrgUnit(
                  squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow());
              inventoryItemRepository.save(inv);
              return material.getId();
            });

    List<InventoryStackAggregate> stacks =
        inventoryItemRepository.findGlobalStacks(
            true, List.of(materialId), null, false, null, false, null, true, null, Set.of());

    assertThat(stacks)
        .as(
            "a non-personal squadron item with null jobOrder/mission must still appear in the"
                + " admin-wide global stack view")
        .hasSize(1);
    assertThat(stacks.get(0).material().getId()).isEqualTo(materialId);
    assertThat(stacks.get(0).totalAmount()).isEqualTo(100.0);
  }

  /**
   * A personal item is, by the inventory invariants, never linked to a job order or mission and may
   * have a {@code null} owning org unit (ownerless personal). It must still surface in the owner's
   * grouped "my inventory" view; the same implicit-join trap on {@code jobOrder} / {@code mission} /
   * {@code owningOrgUnit} would otherwise hide every personal stack.
   */
  @Test
  void findUserStacks_includesPersonalItemWithoutAssociations() {
    UUID[] ids = new UUID[2];
    transactionTemplate.executeWithoutResult(
        status -> {
          User user = new User();
          user.setId(UUID.randomUUID());
          user.setUsername("u-" + UUID.randomUUID());
          userRepository.save(user);

          Location location = new Location();
          location.setName("Hub-" + UUID.randomUUID());
          location = locationRepository.save(location);

          Material material = new Material();
          material.setName("Astatine-" + UUID.randomUUID());
          material.setType(MaterialType.RAW);
          material = materialRepository.save(material);

          InventoryItem inv = new InventoryItem();
          inv.setUser(user);
          inv.setLocation(location);
          inv.setMaterial(material);
          inv.setQuality(500);
          inv.setAmount(42.0);
          inv.setPersonal(true);
          inv.setJobOrder(null);
          inv.setMission(null);
          inv.setOwningOrgUnit(null);
          inventoryItemRepository.save(inv);
          ids[0] = user.getId();
          ids[1] = material.getId();
        });

    List<InventoryStackAggregate> stacks =
        inventoryItemRepository.findUserStacks(
            ids[0], false, null, null, false, null, false, null);

    assertThat(stacks)
        .as(
            "a personal item with null jobOrder/mission/owningOrgUnit must still appear in the"
                + " owner's grouped inventory view")
        .hasSize(1);
    assertThat(stacks.get(0).material().getId()).isEqualTo(ids[1]);
    assertThat(stacks.get(0).totalAmount()).isEqualTo(42.0);
  }
}
