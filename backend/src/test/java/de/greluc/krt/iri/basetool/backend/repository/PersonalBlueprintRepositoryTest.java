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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the multi-owner finders {@link PersonalBlueprintRepository} grew for the
 * blueprint availability overview (#364): {@link PersonalBlueprintRepository#findAllByOwnerSubIn}
 * and {@link PersonalBlueprintRepository#findAllByProductKeyAndOwnerSubIn}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalBlueprintRepositoryTest {

  private static final String OWNER_A = "11111111-1111-1111-1111-111111111111";
  private static final String OWNER_B = "22222222-2222-2222-2222-222222222222";
  private static final String OWNER_C = "33333333-3333-3333-3333-333333333333";

  @Autowired private PersonalBlueprintRepository repository;

  @BeforeEach
  void clean() {
    repository.deleteAll();
  }

  @Test
  void findAllByOwnerSubIn_returnsRowsForGivenOwnersOnly() {
    repository.save(bp(OWNER_A, "aurora", "Aurora MR"));
    repository.save(bp(OWNER_B, "aurora", "Aurora MR"));
    repository.save(bp(OWNER_C, "cutlass", "Cutlass Black"));

    List<PersonalBlueprint> rows = repository.findAllByOwnerSubIn(Set.of(OWNER_A, OWNER_B));

    assertEquals(2, rows.size());
    assertTrue(rows.stream().allMatch(r -> Set.of(OWNER_A, OWNER_B).contains(r.getOwnerSub())));
  }

  @Test
  void findAllByProductKeyAndOwnerSubIn_restrictsToProductAndOwners() {
    repository.save(bp(OWNER_A, "aurora", "Aurora MR"));
    repository.save(bp(OWNER_B, "aurora", "Aurora MR"));
    repository.save(bp(OWNER_C, "aurora", "Aurora MR")); // owner out of scope
    repository.save(bp(OWNER_A, "cutlass", "Cutlass Black")); // other product

    List<PersonalBlueprint> rows =
        repository.findAllByProductKeyAndOwnerSubIn("aurora", Set.of(OWNER_A, OWNER_B));

    Set<String> owners =
        rows.stream().map(PersonalBlueprint::getOwnerSub).collect(Collectors.toSet());
    assertEquals(Set.of(OWNER_A, OWNER_B), owners);
  }

  private static PersonalBlueprint bp(String ownerSub, String productKey, String productName) {
    return PersonalBlueprint.builder()
        .ownerSub(ownerSub)
        .productKey(productKey)
        .productName(productName)
        .build();
  }
}
