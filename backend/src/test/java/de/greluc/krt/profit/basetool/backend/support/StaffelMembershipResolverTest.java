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

package de.greluc.krt.profit.basetool.backend.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link StaffelMembershipResolver}, the single owner of the "name-sorted
 * primary Staffel" rule (REQ-ORG-017). Pins the contract the three former call sites ({@code
 * OrgUnitMembershipService}, {@code UserMapper}, {@code OwnerScopeService}) relied on: empty input,
 * the single-Staffel fast path that must not touch the squadron table, the case-insensitive name
 * ordering for two Staffeln, and the dangling-membership skip.
 */
@ExtendWith(MockitoExtension.class)
class StaffelMembershipResolverTest {

  @Mock private SquadronRepository squadronRepository;

  @InjectMocks private StaffelMembershipResolver resolver;

  @Test
  void resolveNameSortedStaffelIds_empty_returnsEmptyAndNeverHitsSquadronTable() {
    assertTrue(resolver.resolveNameSortedStaffelIds(List.of()).isEmpty());
    verifyNoInteractions(squadronRepository);
  }

  @Test
  void resolveNameSortedStaffelIds_single_returnsItWithoutSquadronLoad() {
    UUID userId = UUID.randomUUID();
    UUID squadronId = UUID.randomUUID();

    List<UUID> result =
        resolver.resolveNameSortedStaffelIds(List.of(staffelRow(userId, squadronId)));

    assertEquals(List.of(squadronId), result);
    // The single-Staffel case is already its own primary — no name sort, no squadron load.
    verifyNoInteractions(squadronRepository);
  }

  @Test
  void resolveNameSortedStaffelIds_two_returnsNameSortedPrimaryFirst() {
    UUID userId = UUID.randomUUID();
    UUID alphaId = UUID.randomUUID();
    UUID bravoId = UUID.randomUUID();
    // Rows + entities in non-alphabetical order to prove the sort decides, not the input order.
    when(squadronRepository.findAllById(any()))
        .thenReturn(List.of(squadron(bravoId, "Bravo"), squadron(alphaId, "alpha")));

    List<UUID> result =
        resolver.resolveNameSortedStaffelIds(
            List.of(staffelRow(userId, bravoId), staffelRow(userId, alphaId)));

    // Case-insensitive: "alpha" sorts before "Bravo".
    assertEquals(List.of(alphaId, bravoId), result);
  }

  @Test
  void resolveNameSortedStaffelIds_two_skipsDanglingRow() {
    UUID userId = UUID.randomUUID();
    UUID aliveId = UUID.randomUUID();
    UUID danglingId = UUID.randomUUID();
    // Only the live squadron resolves; the dangling row is silently dropped by the batch load.
    when(squadronRepository.findAllById(any())).thenReturn(List.of(squadron(aliveId, "Alpha")));

    List<UUID> result =
        resolver.resolveNameSortedStaffelIds(
            List.of(staffelRow(userId, danglingId), staffelRow(userId, aliveId)));

    assertEquals(List.of(aliveId), result);
  }

  @Test
  void resolveNameSortedStaffeln_empty_returnsEmptyAndNeverHitsSquadronTable() {
    assertTrue(resolver.resolveNameSortedStaffeln(List.of()).isEmpty());
    verifyNoInteractions(squadronRepository);
  }

  @Test
  void resolveNameSortedStaffeln_two_returnsNameSortedEntities() {
    UUID userId = UUID.randomUUID();
    UUID alphaId = UUID.randomUUID();
    UUID bravoId = UUID.randomUUID();
    Squadron alpha = squadron(alphaId, "Alpha");
    Squadron bravo = squadron(bravoId, "Bravo");
    when(squadronRepository.findAllById(any())).thenReturn(List.of(bravo, alpha));

    List<Squadron> result =
        resolver.resolveNameSortedStaffeln(
            List.of(staffelRow(userId, bravoId), staffelRow(userId, alphaId)));

    assertEquals(List.of(alpha, bravo), result);
    // The single-row fast path of the id variant does NOT apply here — the entity variant always
    // batch-loads, even for one row, because the caller needs the squadron's name + shorthand.
    verify(squadronRepository).findAllById(any());
  }

  /** Builds a {@code SQUADRON}-kind membership row pointing the user at the given squadron. */
  private static OrgUnitMembership staffelRow(UUID userId, UUID squadronId) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(userId, squadronId));
    m.setKind(OrgUnitKind.SQUADRON);
    return m;
  }

  /** Builds a squadron fixture with the given id and name. */
  private static Squadron squadron(UUID id, String name) {
    Squadron s = new Squadron();
    s.setId(id);
    s.setName(name);
    return s;
  }
}
