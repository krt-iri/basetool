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

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintOverviewOwnerDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Mockito unit tests for {@link PersonalBlueprintOverviewService} — the in-Java aggregation and
 * scope-driven owner resolution. The oversight {@link ScopePredicate} is supplied by a mocked
 * {@link OwnerScopeService}; the persona-specific predicate construction itself is covered by
 * {@code OwnerScopeServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class PersonalBlueprintOverviewServiceTest {

  @Mock private OwnerScopeService ownerScopeService;
  @Mock private OrgUnitMembershipRepository orgUnitMembershipRepository;
  @Mock private PersonalBlueprintRepository personalBlueprintRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private PersonalBlueprintOverviewService service;

  private static final UUID USER_1 = UUID.randomUUID();
  private static final UUID USER_2 = UUID.randomUUID();
  private static final UUID ORG_A = UUID.randomUUID();

  private static PersonalBlueprint bp(String key, String name, UUID owner) {
    PersonalBlueprint b = new PersonalBlueprint();
    b.setProductKey(key);
    b.setProductName(name);
    b.setOwnerSub(owner.toString());
    return b;
  }

  private static User user(UUID id, String displayName) {
    User u = new User();
    u.setId(id);
    u.setDisplayName(displayName);
    return u;
  }

  private static Pageable byName() {
    return PageRequest.of(0, 50, Sort.by("productName"));
  }

  @Test
  void list_adminAllScope_aggregatesEveryOwner_includingOrgUnitLess() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    // Admin "all org units" must span EVERY blueprint owner — including USER_2, who holds no
    // org-unit membership. Resolving via the org-unit member list (the #371 bug) silently dropped
    // such owners, so a squadron-less admin's own blueprints went missing.
    when(personalBlueprintRepository.findAllDistinctOwnerSubs())
        .thenReturn(Set.of(USER_1.toString(), USER_2.toString()));
    when(personalBlueprintRepository.findAllByOwnerSubIn(any()))
        .thenReturn(
            List.of(
                bp("aurora", "Aurora MR", USER_1),
                bp("aurora", "Aurora MR", USER_2),
                bp("cutlass", "Cutlass Black", USER_1)));

    Page<BlueprintOverviewEntryDto> page = service.listAvailableBlueprints(byName());

    assertEquals(2, page.getTotalElements());
    assertEquals("Aurora MR", page.getContent().get(0).productName());
    assertEquals(2L, page.getContent().get(0).ownerCount());
    assertEquals("Cutlass Black", page.getContent().get(1).productName());
    assertEquals(1L, page.getContent().get(1).ownerCount());
    verify(orgUnitMembershipRepository, never()).findDistinctUserIdsByOrgUnitIdIn(any());
  }

  @Test
  void list_pinnedScope_resolvesMembersOfPinnedOrgUnit() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(false, ORG_A, Set.of()));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_A)))
        .thenReturn(Set.of(USER_1));
    when(personalBlueprintRepository.findAllByOwnerSubIn(any()))
        .thenReturn(List.of(bp("aurora", "Aurora MR", USER_1)));

    Page<BlueprintOverviewEntryDto> page = service.listAvailableBlueprints(byName());

    assertEquals(1, page.getTotalElements());
    verify(orgUnitMembershipRepository).findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_A));
  }

  @Test
  void list_emptyOversight_returnsEmptyWithoutQueryingBlueprints() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));

    Page<BlueprintOverviewEntryDto> page = service.listAvailableBlueprints(byName());

    assertTrue(page.getContent().isEmpty());
    assertEquals(0, page.getTotalElements());
    verify(personalBlueprintRepository, never()).findAllByOwnerSubIn(any());
  }

  @Test
  void list_descendingSort_reversesByName() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    when(personalBlueprintRepository.findAllDistinctOwnerSubs())
        .thenReturn(Set.of(USER_1.toString()));
    when(personalBlueprintRepository.findAllByOwnerSubIn(any()))
        .thenReturn(
            List.of(bp("aurora", "Aurora MR", USER_1), bp("cutlass", "Cutlass Black", USER_1)));

    Page<BlueprintOverviewEntryDto> page =
        service.listAvailableBlueprints(PageRequest.of(0, 50, Sort.by("productName").descending()));

    assertEquals("Cutlass Black", page.getContent().get(0).productName());
    assertEquals("Aurora MR", page.getContent().get(1).productName());
  }

  @Test
  void owners_returnsDistinctMembersByDisplayNameSorted() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ORG_A)));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_A)))
        .thenReturn(Set.of(USER_1, USER_2));
    when(personalBlueprintRepository.findAllByProductKeyAndOwnerSubIn(eq("aurora"), any()))
        .thenReturn(List.of(bp("aurora", "Aurora MR", USER_1), bp("aurora", "Aurora MR", USER_2)));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(user(USER_1, "Bravo"), user(USER_2, "Alpha")));

    List<BlueprintOverviewOwnerDto> owners = service.listOwnersForProduct("aurora");

    assertEquals(
        List.of("Alpha", "Bravo"),
        owners.stream().map(BlueprintOverviewOwnerDto::ownerName).toList());
  }

  @Test
  void owners_emptyOversight_returnsEmptyWithoutQueries() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of()));

    assertTrue(service.listOwnersForProduct("aurora").isEmpty());
    verify(personalBlueprintRepository, never()).findAllByProductKeyAndOwnerSubIn(any(), any());
    verify(userRepository, never()).findAllById(any());
  }

  @Test
  void owners_nobodyInScopeOwnsProduct_returnsEmpty() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(false, null, Set.of(ORG_A)));
    when(orgUnitMembershipRepository.findDistinctUserIdsByOrgUnitIdIn(Set.of(ORG_A)))
        .thenReturn(Set.of(USER_1));
    when(personalBlueprintRepository.findAllByProductKeyAndOwnerSubIn(eq("aurora"), any()))
        .thenReturn(List.of());

    assertTrue(service.listOwnersForProduct("aurora").isEmpty());
    verify(userRepository, never()).findAllById(any());
  }

  // covers REQ-INV-012 — the admin drill-down queries by product key alone, without the
  // all-owners pre-scan + unbounded IN list that made every expand click slow.
  @Test
  void owners_adminAllScope_listsOwnersAcrossEveryOrgUnit_byProductKeyAlone() {
    when(ownerScopeService.currentBlueprintOversightScope())
        .thenReturn(new ScopePredicate(true, null, Set.of()));
    // The drill-down for the admin "all org units" scope still spans every blueprint owner, not
    // just org-unit members (#371 fix) — but via the direct product-key lookup.
    when(personalBlueprintRepository.findAllByProductKey("aurora"))
        .thenReturn(List.of(bp("aurora", "Aurora MR", USER_1), bp("aurora", "Aurora MR", USER_2)));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(user(USER_1, "Bravo"), user(USER_2, "Alpha")));

    List<BlueprintOverviewOwnerDto> owners = service.listOwnersForProduct("aurora");

    assertEquals(
        List.of("Alpha", "Bravo"),
        owners.stream().map(BlueprintOverviewOwnerDto::ownerName).toList());
    verify(orgUnitMembershipRepository, never()).findDistinctUserIdsByOrgUnitIdIn(any());
    verify(personalBlueprintRepository, never()).findAllDistinctOwnerSubs();
    verify(personalBlueprintRepository, never()).findAllByProductKeyAndOwnerSubIn(any(), any());
  }
}
