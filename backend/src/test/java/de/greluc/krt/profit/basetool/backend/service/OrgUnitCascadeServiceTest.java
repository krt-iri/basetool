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

package de.greluc.krt.profit.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link OrgUnitCascadeService} — the single, shared definition of the
 * org-hierarchy scope cascade (epic #692, REQ-ORG-015). Verifies the exact reach a leadership
 * membership confers downward, the strict-silo isolation between Bereiche, the OL "everything"
 * branch, and — most importantly for the zero-regression mandate — that a caller with no leadership
 * flag is expanded to exactly their direct memberships with no hierarchy read at all.
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitCascadeServiceTest {

  @Mock private OrgUnitRepository orgUnitRepository;

  @InjectMocks private OrgUnitCascadeService service;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID BEREICH_A_ID = UUID.randomUUID();
  private static final UUID BEREICH_B_ID = UUID.randomUUID();
  private static final UUID OL_ID = UUID.randomUUID();
  private static final UUID STAFFEL_A1_ID = UUID.randomUUID();
  private static final UUID STAFFEL_A2_ID = UUID.randomUUID();
  private static final UUID SK_A1_ID = UUID.randomUUID();
  private static final UUID STAFFEL_B1_ID = UUID.randomUUID();
  private static final UUID FOREIGN_STAFFEL_ID = UUID.randomUUID();

  /**
   * Builds a membership row of the given kind for {@link #USER_ID} pointing at {@code orgUnitId}.
   */
  private static OrgUnitMembership membership(UUID orgUnitId, OrgUnitKind kind) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(USER_ID, orgUnitId));
    m.setKind(kind);
    return m;
  }

  @Test
  void plainStaffelMembership_expandsToItselfOnly_noHierarchyRead() {
    OrgUnitMembership staffel = membership(STAFFEL_A1_ID, OrgUnitKind.SQUADRON);

    Set<UUID> reach = service.expandWithDescendants(List.of(staffel));

    assertEquals(Set.of(STAFFEL_A1_ID), reach);
    assertTrue(service.cascadedOfficerReach(List.of(staffel)).isEmpty());
    // Zero-regression guarantee: a flag-less caller triggers no hierarchy expansion query at all.
    verify(orgUnitRepository, never()).findChildOrgUnitIds(org.mockito.ArgumentMatchers.any());
    verify(orgUnitRepository, never()).findAllOrgUnitIds();
  }

  @Test
  void emptyMemberships_expandToEmpty() {
    assertTrue(service.expandWithDescendants(List.of()).isEmpty());
    assertTrue(service.cascadedOfficerReach(List.of()).isEmpty());
  }

  @Test
  void bereichsleiter_reachesBereichAndAllItsChildren() {
    OrgUnitMembership lead = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    lead.setBereichsleiter(true);
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_A_ID))
        .thenReturn(List.of(STAFFEL_A1_ID, STAFFEL_A2_ID, SK_A1_ID));

    Set<UUID> reach = service.expandWithDescendants(List.of(lead));

    assertEquals(Set.of(BEREICH_A_ID, STAFFEL_A1_ID, STAFFEL_A2_ID, SK_A1_ID), reach);
    assertEquals(
        Set.of(BEREICH_A_ID, STAFFEL_A1_ID, STAFFEL_A2_ID, SK_A1_ID),
        service.cascadedOfficerReach(List.of(lead)));
  }

  @Test
  void bereichskoordinatorAndOperatorFlags_alsoCascade() {
    OrgUnitMembership koord = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    koord.setBereichskoordinator(true);
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_A_ID)).thenReturn(List.of(STAFFEL_A1_ID));

    assertEquals(Set.of(BEREICH_A_ID, STAFFEL_A1_ID), service.cascadedOfficerReach(List.of(koord)));

    OrgUnitMembership op = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    op.setBereichsoperator(true);
    assertEquals(Set.of(BEREICH_A_ID, STAFFEL_A1_ID), service.cascadedOfficerReach(List.of(op)));
  }

  @Test
  void flagLessBereichSeat_doesNotCascade() {
    // The organisational, chart-only Bereichsleitung seat an SK-Leiter holds (REQ-ORG-017, Q1):
    // it is a BEREICH membership with NO leadership flag, so it must NOT widen reach.
    OrgUnitMembership seat = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);

    assertEquals(Set.of(BEREICH_A_ID), service.expandWithDescendants(List.of(seat)));
    assertTrue(service.cascadedOfficerReach(List.of(seat)).isEmpty());
    verify(orgUnitRepository, never()).findChildOrgUnitIds(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void strictSilo_bereichsleiterDoesNotReachOtherBereichOrItsChildren() {
    OrgUnitMembership leadA = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    leadA.setBereichsleiter(true);
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_A_ID)).thenReturn(List.of(STAFFEL_A1_ID));

    Set<UUID> reach = service.expandWithDescendants(List.of(leadA));

    // Only Bereich A and its child — never Bereich B, its child, or a foreign Staffel.
    assertTrue(reach.contains(BEREICH_A_ID));
    assertTrue(reach.contains(STAFFEL_A1_ID));
    org.junit.jupiter.api.Assertions.assertFalse(reach.contains(BEREICH_B_ID));
    org.junit.jupiter.api.Assertions.assertFalse(reach.contains(STAFFEL_B1_ID));
    org.junit.jupiter.api.Assertions.assertFalse(reach.contains(FOREIGN_STAFFEL_ID));
    // The other Bereich's children are never queried.
    verify(orgUnitRepository, never()).findChildOrgUnitIds(BEREICH_B_ID);
  }

  @Test
  void olMember_reachesEveryOrgUnit_viaConcreteUnion_notAdminMarker() {
    OrgUnitMembership ol = membership(OL_ID, OrgUnitKind.ORGANISATIONSLEITUNG);
    ol.setOlMember(true);
    when(orgUnitRepository.findAllOrgUnitIds())
        .thenReturn(
            List.of(
                OL_ID,
                BEREICH_A_ID,
                BEREICH_B_ID,
                STAFFEL_A1_ID,
                STAFFEL_B1_ID,
                FOREIGN_STAFFEL_ID));

    Set<UUID> reach = service.expandWithDescendants(List.of(ol));

    assertEquals(
        Set.of(OL_ID, BEREICH_A_ID, BEREICH_B_ID, STAFFEL_A1_ID, STAFFEL_B1_ID, FOREIGN_STAFFEL_ID),
        reach);
    // OL short-circuits to the all-ids query and never walks individual Bereich children.
    verify(orgUnitRepository, never()).findChildOrgUnitIds(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void multipleBereichLeaderships_unionAllTheirChildren() {
    OrgUnitMembership leadA = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    leadA.setBereichsleiter(true);
    OrgUnitMembership leadB = membership(BEREICH_B_ID, OrgUnitKind.BEREICH);
    leadB.setBereichskoordinator(true);
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_A_ID)).thenReturn(List.of(STAFFEL_A1_ID));
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_B_ID)).thenReturn(List.of(STAFFEL_B1_ID));

    Set<UUID> reach = service.cascadedOfficerReach(List.of(leadA, leadB));

    assertEquals(Set.of(BEREICH_A_ID, STAFFEL_A1_ID, BEREICH_B_ID, STAFFEL_B1_ID), reach);
  }

  @Test
  void mixedMemberships_combineDirectAndCascade() {
    // A user who both belongs to a Staffel directly AND leads a Bereich.
    OrgUnitMembership ownStaffel = membership(FOREIGN_STAFFEL_ID, OrgUnitKind.SQUADRON);
    OrgUnitMembership lead = membership(BEREICH_A_ID, OrgUnitKind.BEREICH);
    lead.setBereichsleiter(true);
    when(orgUnitRepository.findChildOrgUnitIds(BEREICH_A_ID)).thenReturn(List.of(STAFFEL_A1_ID));

    Set<UUID> reach = service.expandWithDescendants(List.of(ownStaffel, lead));

    assertEquals(Set.of(FOREIGN_STAFFEL_ID, BEREICH_A_ID, STAFFEL_A1_ID), reach);
    // The direct Staffel is NOT part of the cascade reach (it confers no downward leadership).
    org.junit.jupiter.api.Assertions.assertFalse(
        service.cascadedOfficerReach(List.of(ownStaffel, lead)).contains(FOREIGN_STAFFEL_ID));
  }
}
