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

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderBlueprintOwnerDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemBlueprintOwnersDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderRequiredBlueprintDto;
import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link JobOrderItemBlueprintOwnersService}: the product-key matching of an
 * item order's required lines against members' owned blueprints, the per-owner grouping, the
 * per-product coverage counting, and the responsible-org-unit-only member resolution. The real
 * {@link BlueprintNameNormalizer} is wired in so the normalized-key matching is exercised
 * end-to-end; the surrounding members-only authorization is covered by {@code
 * OwnerScopeServiceTest} and {@code JobOrderControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class JobOrderItemBlueprintOwnersServiceTest {

  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private OrgUnitMembershipRepository orgUnitMembershipRepository;
  @Mock private PersonalBlueprintRepository personalBlueprintRepository;
  @Mock private UserRepository userRepository;

  private JobOrderItemBlueprintOwnersService service;

  @BeforeEach
  void setUp() {
    // Wire the service by hand (not @InjectMocks) so the real BlueprintNameNormalizer drives the
    // normalized-key matching end-to-end; constructed here, after Mockito has populated the mocks.
    service =
        new JobOrderItemBlueprintOwnersService(
            jobOrderRepository,
            orgUnitMembershipRepository,
            personalBlueprintRepository,
            userRepository,
            new BlueprintNameNormalizer());
  }

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID ALICE = UUID.randomUUID();
  private static final UUID BERND = UUID.randomUUID();

  private static Blueprint blueprint(String outputName) {
    Blueprint b = new Blueprint();
    b.setOutputName(outputName);
    return b;
  }

  private static GameItem gameItem(String name) {
    GameItem g = new GameItem();
    g.setName(name);
    return g;
  }

  private static JobOrderItem item(String outputName, String displayName) {
    return JobOrderItem.builder()
        .blueprint(blueprint(outputName))
        .gameItem(gameItem(displayName))
        .amount(1)
        .build();
  }

  private static JobOrder order(JobOrderItem... items) {
    Squadron responsible = new Squadron();
    responsible.setId(ORG_ID);
    return JobOrder.builder()
        .id(ORDER_ID)
        .responsibleOrgUnit(responsible)
        .items(new LinkedHashSet<>(List.of(items)))
        .build();
  }

  private static PersonalBlueprint owned(UUID owner, String productKey) {
    PersonalBlueprint b = new PersonalBlueprint();
    b.setOwnerSub(owner.toString());
    b.setProductKey(productKey);
    b.setProductName(productKey);
    return b;
  }

  private static User user(UUID id, String displayName) {
    User u = new User();
    u.setId(id);
    u.setDisplayName(displayName);
    return u;
  }

  @Test
  void itemOrderWithNoBlueprintProducts_returnsEmptyWithoutMemberLookup() {
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order()));

    JobOrderItemBlueprintOwnersDto result = service.getBlueprintOwners(ORDER_ID);

    assertTrue(result.requiredBlueprints().isEmpty());
    assertTrue(result.owners().isEmpty());
    verify(orgUnitMembershipRepository, never()).findDistinctUserIdsByOrgUnitIdIn(any());
    verify(personalBlueprintRepository, never()).findAllByOwnerSubInAndProductKeyIn(any(), any());
  }

  @Test
  void groupsOwnersAndCountsCoverage_matchingByNormalizedProductKey() {
    // Two required items; the blueprint output names are deliberately messy (extra spacing, case)
    // so the run exercises the normalizer: "Aurora   MR" -> "aurora mr", "Cutlass Black" ->
    // "cutlass black". The display label comes from the requested game item, not the owned-row
    // name.
    JobOrder order =
        order(item("Aurora   MR", "Aurora MR Ship"), item("Cutlass Black", "Cutlass Black Ship"));
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID)))
        .thenReturn(Set.of(ALICE, BERND));
    // Alice owns both required products; Bernd owns only Aurora.
    when(personalBlueprintRepository.findAllByOwnerSubInAndProductKeyIn(any(), any()))
        .thenReturn(
            List.of(
                owned(ALICE, "aurora mr"),
                owned(ALICE, "cutlass black"),
                owned(BERND, "aurora mr")));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(user(ALICE, "Alice"), user(BERND, "Bernd")));

    JobOrderItemBlueprintOwnersDto result = service.getBlueprintOwners(ORDER_ID);

    // Coverage: Aurora owned by 2, Cutlass by 1. Sorted by display name (Aurora < Cutlass).
    List<JobOrderRequiredBlueprintDto> coverage = result.requiredBlueprints();
    assertEquals(2, coverage.size());
    assertEquals("Aurora MR Ship", coverage.get(0).productName());
    assertEquals("aurora mr", coverage.get(0).productKey());
    assertEquals(2, coverage.get(0).ownerCount());
    assertEquals("Cutlass Black Ship", coverage.get(1).productName());
    assertEquals(1, coverage.get(1).ownerCount());

    // Owners: person-centric, sorted by name; the owned-product list uses the order's display
    // names.
    List<JobOrderBlueprintOwnerDto> owners = result.owners();
    assertEquals(2, owners.size());
    assertEquals("Alice", owners.get(0).ownerName());
    assertEquals(
        List.of("Aurora MR Ship", "Cutlass Black Ship"), owners.get(0).ownedProductNames());
    assertEquals("Bernd", owners.get(1).ownerName());
    assertEquals(List.of("Aurora MR Ship"), owners.get(1).ownedProductNames());
  }

  @Test
  void productNobodyOwns_isFlaggedAsCoverageGap() {
    JobOrder order = order(item("Aurora MR", "Aurora MR Ship"), item("Idris", "Idris Frigate"));
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID)))
        .thenReturn(Set.of(ALICE));
    when(personalBlueprintRepository.findAllByOwnerSubInAndProductKeyIn(any(), any()))
        .thenReturn(List.of(owned(ALICE, "aurora mr")));
    when(userRepository.findAllById(any())).thenReturn(List.of(user(ALICE, "Alice")));

    JobOrderItemBlueprintOwnersDto result = service.getBlueprintOwners(ORDER_ID);

    JobOrderRequiredBlueprintDto idris =
        result.requiredBlueprints().stream()
            .filter(rb -> rb.productKey().equals("idris"))
            .findFirst()
            .orElseThrow();
    assertEquals(0, idris.ownerCount());
    // Only Alice (who owns a required product) is listed; she does not own Idris.
    assertEquals(1, result.owners().size());
    assertEquals(List.of("Aurora MR Ship"), result.owners().get(0).ownedProductNames());
  }

  @Test
  void memberOwningNoneOfTheRequiredProducts_isNotListed() {
    JobOrder order = order(item("Aurora MR", "Aurora MR Ship"));
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID)))
        .thenReturn(Set.of(ALICE, BERND));
    // Only Alice owns the required product; Bernd owns nothing relevant (repo returns only
    // matches).
    when(personalBlueprintRepository.findAllByOwnerSubInAndProductKeyIn(any(), any()))
        .thenReturn(List.of(owned(ALICE, "aurora mr")));
    when(userRepository.findAllById(any())).thenReturn(List.of(user(ALICE, "Alice")));

    JobOrderItemBlueprintOwnersDto result = service.getBlueprintOwners(ORDER_ID);

    assertEquals(1, result.owners().size());
    assertEquals("Alice", result.owners().get(0).ownerName());
  }

  @Test
  void resolvesMembersOfTheResponsibleOrgUnitOnly() {
    JobOrder order = order(item("Aurora MR", "Aurora MR Ship"));
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID)))
        .thenReturn(Set.of(ALICE));
    when(personalBlueprintRepository.findAllByOwnerSubInAndProductKeyIn(
            eq(Set.of(ALICE.toString())), any()))
        .thenReturn(List.of(owned(ALICE, "aurora mr")));
    when(userRepository.findAllById(any())).thenReturn(List.of(user(ALICE, "Alice")));

    service.getBlueprintOwners(ORDER_ID);

    verify(orgUnitMembershipRepository).findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID));
    verify(personalBlueprintRepository)
        .findAllByOwnerSubInAndProductKeyIn(eq(Set.of(ALICE.toString())), any());
  }

  @Test
  void noMembers_yieldsZeroCoverageAndNoOwnersWithoutBlueprintQuery() {
    JobOrder order = order(item("Aurora MR", "Aurora MR Ship"));
    when(jobOrderRepository.findByIdWithItemBlueprints(ORDER_ID)).thenReturn(Optional.of(order));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_ID)))
        .thenReturn(Set.of());

    JobOrderItemBlueprintOwnersDto result = service.getBlueprintOwners(ORDER_ID);

    assertEquals(1, result.requiredBlueprints().size());
    assertEquals(0, result.requiredBlueprints().get(0).ownerCount());
    assertTrue(result.owners().isEmpty());
    verify(personalBlueprintRepository, never()).findAllByOwnerSubInAndProductKeyIn(any(), any());
  }
}
