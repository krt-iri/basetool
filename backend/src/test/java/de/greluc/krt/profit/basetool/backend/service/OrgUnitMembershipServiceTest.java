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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.Bereich;
import de.greluc.krt.profit.basetool.backend.model.KommandoGroup;
import de.greluc.krt.profit.basetool.backend.model.MembershipRole;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.profit.basetool.backend.model.Organisationsleitung;
import de.greluc.krt.profit.basetool.backend.model.SpecialCommand;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.BereichLeadershipRole;
import de.greluc.krt.profit.basetool.backend.model.dto.MembershipFlagsPatchRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.MembershipLeadToggleRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.profit.basetool.backend.repository.KommandoGroupRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.profit.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Mockito unit tests for {@link OrgUnitMembershipService}. Pins the CRUD contract that the SK
 * member-management UI relies on: listing through the SK existence guard, add/remove happy paths
 * plus the duplicate-409 and not-found-404 paths, the flag-patch semantics including
 * optimistic-lock failures, and the dedicated lead toggle.
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitMembershipServiceTest {

  @Mock private OrgUnitMembershipRepository membershipRepository;
  @Mock private SpecialCommandService specialCommandService;
  @Mock private UserRepository userRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private SpecialCommandRepository specialCommandRepository;
  @Mock private OrgUnitRepository orgUnitRepository;
  @Mock private KommandoGroupRepository kommandoGroupRepository;
  @Mock private OrgUnitCascadeService orgUnitCascadeService;
  @Mock private InventoryOrgUnitReconciler inventoryReconciler;
  @Mock private AuditService auditService;
  @Mock private OrgChartService orgChartService;

  @InjectMocks private OrgUnitMembershipService membershipService;

  private SpecialCommand sc;
  private UUID scId;
  private User user;
  private UUID userId;
  private OrgUnitMembershipId id;

  @BeforeEach
  void setUp() {
    scId = UUID.randomUUID();
    sc = new SpecialCommand();
    sc.setId(scId);
    sc.setName("Alpha");
    sc.setShorthand("ALF");

    userId = UUID.randomUUID();
    user = new User();
    user.setId(userId);
    user.setUsername("alice");
    user.setDisplayName("Alice");

    id = new OrgUnitMembershipId(userId, scId);
  }

  // --- listMembers ----------------------------------------------------------

  @Test
  void listMembers_existingSc_returnsMembers() {
    OrgUnitMembership m = new OrgUnitMembership();
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findAllByIdOrgUnitId(scId)).thenReturn(List.of(m));

    List<OrgUnitMembership> result = membershipService.listMembers(scId);

    assertEquals(1, result.size());
    assertSame(m, result.get(0));
  }

  @Test
  void listMembers_unknownSc_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId))
        .thenThrow(new NotFoundException("SpecialCommand not found"));

    assertThrows(NotFoundException.class, () -> membershipService.listMembers(scId));
    verify(membershipRepository, never()).findAllByIdOrgUnitId(any());
  }

  // --- addMember ------------------------------------------------------------

  @Test
  void addMember_freshUser_persistsMembership() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(false);
    when(membershipRepository.save(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership saved = membershipService.addMember(scId, userId);

    assertSame(user, saved.getUser());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, saved.getKind());
    assertNotNull(saved.getJoinedAt());
    assertEquals(userId, saved.getId().getUserId());
    assertEquals(scId, saved.getId().getOrgUnitId());
    verify(membershipRepository).save(any(OrgUnitMembership.class));
    verify(auditService)
        .record(eq(AuditEventType.MEMBERSHIP_GRANTED), eq(scId), any(), eq(userId), any());
  }

  @Test
  void addMember_alreadyMember_throwsDuplicate() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(true);

    assertThrows(DuplicateEntityException.class, () -> membershipService.addMember(scId, userId));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void addMember_unknownUser_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> membershipService.addMember(scId, userId));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void addMember_unknownSc_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId))
        .thenThrow(new NotFoundException("SpecialCommand not found"));

    assertThrows(NotFoundException.class, () -> membershipService.addMember(scId, userId));
    verify(userRepository, never()).findById(any());
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void addMember_firstMembership_promotesOwnerlessInventory() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(false);
    when(membershipRepository.countByIdUserId(userId)).thenReturn(0L);
    when(membershipRepository.save(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    membershipService.addMember(scId, userId);

    verify(inventoryReconciler).onUserGainedFirstOrgUnit(userId, sc);
  }

  @Test
  void addMember_userAlreadyHadMemberships_doesNotPromoteInventory() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, scId)).thenReturn(false);
    when(membershipRepository.countByIdUserId(userId)).thenReturn(2L);
    when(membershipRepository.save(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    membershipService.addMember(scId, userId);

    verify(inventoryReconciler, never()).onUserGainedFirstOrgUnit(any(), any());
  }

  // --- removeMember ---------------------------------------------------------

  @Test
  void removeMember_existing_deletes() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(true);

    membershipService.removeMember(scId, userId);

    verify(membershipRepository).deleteById(id);
    verify(orgChartService).mirrorRemoveUnitSeat(scId, userId);
    verify(auditService)
        .record(eq(AuditEventType.MEMBERSHIP_REVOKED), eq(scId), any(), eq(userId), any());
  }

  @Test
  void removeMember_nonMember_throwsNotFound() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> membershipService.removeMember(scId, userId));
    verify(membershipRepository, never()).deleteById(any(OrgUnitMembershipId.class));
  }

  @Test
  void removeMember_lastMembership_demotesInventoryToPersonal() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(true);
    when(membershipRepository.countByIdUserId(userId)).thenReturn(0L);

    membershipService.removeMember(scId, userId);

    verify(inventoryReconciler).onUserLostLastOrgUnit(userId);
  }

  @Test
  void removeMember_userStillHasMemberships_doesNotDemoteInventory() {
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.existsById(id)).thenReturn(true);
    when(membershipRepository.countByIdUserId(userId)).thenReturn(1L);

    membershipService.removeMember(scId, userId);

    verify(inventoryReconciler, never()).onUserLostLastOrgUnit(any());
  }

  // --- patchFlags -----------------------------------------------------------

  @Test
  void patchFlags_bothFlagsSet_updatesBoth() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(3L);
    m.setLogistician(false);
    m.setMissionManager(false);
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, true, 3L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.patchFlags(scId, userId, request);

    assertTrue(updated.isLogistician());
    assertTrue(updated.isMissionManager());
    verify(auditService)
        .record(eq(AuditEventType.CAPABILITY_FLAGS_CHANGED), eq(scId), any(), eq(userId), any());
  }

  @Test
  void patchFlags_onlyLogistician_leavesMissionManagerAlone() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(0L);
    m.setLogistician(false);
    m.setMissionManager(true); // pre-existing true
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.patchFlags(scId, userId, request);

    assertTrue(updated.isLogistician());
    assertTrue(updated.isMissionManager(), "missionManager must stay true when not in payload");
  }

  @Test
  void patchFlags_staleVersion_throwsOptimisticLock() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(5L);
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> membershipService.patchFlags(scId, userId, request));
    verify(membershipRepository, never()).save(any());
  }

  @Test
  void patchFlags_unknownMembership_throwsNotFound() {
    MembershipFlagsPatchRequest request = new MembershipFlagsPatchRequest(true, null, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> membershipService.patchFlags(scId, userId, request));
  }

  // --- toggleLead -----------------------------------------------------------

  @Test
  void toggleLead_promotes() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(0L);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.toggleLead(scId, userId, request);

    assertEquals(MembershipRole.SK_LEAD, updated.getRole());
    verify(orgChartService).mirrorSkLead(scId, userId, true);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_GRANTED), eq(scId), any(), eq(userId), any());
  }

  @Test
  void toggleLead_demotes() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(2L);
    m.setRole(MembershipRole.SK_LEAD);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(false, 2L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.save(m)).thenReturn(m);

    OrgUnitMembership updated = membershipService.toggleLead(scId, userId, request);

    assertEquals(MembershipRole.MEMBER, updated.getRole());
    verify(orgChartService).mirrorSkLead(scId, userId, false);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_REVOKED), eq(scId), any(), eq(userId), any());
  }

  @Test
  void toggleLead_staleVersion_throwsOptimisticLock() {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(5L);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> membershipService.toggleLead(scId, userId, request));
  }

  @Test
  void toggleLead_userHoldsStaffel_throwsBadRequest() {
    // REQ-ORG-017: an SK-Leiter holds no Staffel — promoting a user who still belongs to a Staffel
    // is rejected with a clean 400 (the V165 trigger is the DB backstop).
    OrgUnitMembership m = new OrgUnitMembership();
    m.setVersion(0L);
    MembershipLeadToggleRequest request = new MembershipLeadToggleRequest(true, 0L);
    when(specialCommandService.getSpecialCommandById(scId)).thenReturn(sc);
    when(membershipRepository.findById(id)).thenReturn(Optional.of(m));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of(new OrgUnitMembership()));

    assertThrows(
        BadRequestException.class, () -> membershipService.toggleLead(scId, userId, request));
    verify(membershipRepository, never()).save(any());
  }

  // --- syncStaffelMembership guard ------------------------------------------

  @Test
  void syncStaffelMembership_userHoldsLeadershipRole_throwsBadRequest() {
    // REQ-ORG-017: a leader (SK-Lead/Bereichsleitung/OL) is never assigned to a Staffel.
    Squadron target = new Squadron();
    target.setId(UUID.randomUUID());
    OrgUnitMembership leadRow = new OrgUnitMembership();
    leadRow.setRole(MembershipRole.SK_LEAD);
    when(membershipRepository.countByIdUserId(userId)).thenReturn(1L);
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of());
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(leadRow));

    assertThrows(
        BadRequestException.class, () -> membershipService.syncStaffelMembership(user, target));
    verify(membershipRepository, never()).save(any());
  }

  // --- Bereich / OL leadership membership -----------------------------------

  @Test
  void addBereichLeader_setsExactlyOneRoleFlag() {
    UUID bereichId = UUID.randomUUID();
    Bereich bereich = new Bereich();
    bereich.setId(bereichId);
    when(orgUnitRepository.findById(bereichId)).thenReturn(Optional.of(bereich));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of());
    when(membershipRepository.findById(any(OrgUnitMembershipId.class)))
        .thenReturn(Optional.empty());
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership m =
        membershipService.addBereichLeader(bereichId, userId, BereichLeadershipRole.KOORDINATOR);

    assertEquals(MembershipRole.BEREICHSKOORDINATOR, m.getRole());
    verify(orgChartService).mirrorBereichRole(bereichId, userId, BereichLeadershipRole.KOORDINATOR);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_GRANTED), eq(bereichId), any(), eq(userId), any());
  }

  @Test
  void addBereichLeader_existingRank_recordsRoleChanged() {
    UUID bereichId = UUID.randomUUID();
    Bereich bereich = new Bereich();
    bereich.setId(bereichId);
    OrgUnitMembership existing = new OrgUnitMembership();
    existing.setId(new OrgUnitMembershipId(userId, bereichId));
    existing.setKind(OrgUnitKind.BEREICH);
    existing.setRole(MembershipRole.BEREICHSLEITER);
    when(orgUnitRepository.findById(bereichId)).thenReturn(Optional.of(bereich));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of());
    when(membershipRepository.findById(any(OrgUnitMembershipId.class)))
        .thenReturn(Optional.of(existing));
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    membershipService.addBereichLeader(bereichId, userId, BereichLeadershipRole.KOORDINATOR);

    // An existing leadership rank changed (BEREICHSLEITER -> BEREICHSKOORDINATOR) records CHANGED.
    verify(auditService)
        .record(eq(AuditEventType.ROLE_CHANGED), eq(bereichId), any(), eq(userId), any());
  }

  @Test
  void addBereichLeader_userHoldsStaffel_throwsBadRequest() {
    UUID bereichId = UUID.randomUUID();
    Bereich bereich = new Bereich();
    bereich.setId(bereichId);
    when(orgUnitRepository.findById(bereichId)).thenReturn(Optional.of(bereich));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of(new OrgUnitMembership()));

    assertThrows(
        BadRequestException.class,
        () -> membershipService.addBereichLeader(bereichId, userId, BereichLeadershipRole.LEITER));
    verify(membershipRepository, never()).saveAndFlush(any());
    // A rejected assignment must not write an audit event.
    verify(auditService, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void addBereichLeader_notABereich_throwsBadRequest() {
    UUID notBereichId = UUID.randomUUID();
    Squadron squadron = new Squadron();
    squadron.setId(notBereichId);
    when(orgUnitRepository.findById(notBereichId)).thenReturn(Optional.of(squadron));

    assertThrows(
        BadRequestException.class,
        () ->
            membershipService.addBereichLeader(notBereichId, userId, BereichLeadershipRole.LEITER));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  @Test
  void addOlMember_setsOlFlag() {
    UUID olId = UUID.randomUUID();
    Organisationsleitung ol = new Organisationsleitung();
    ol.setId(olId);
    when(orgUnitRepository.findById(olId)).thenReturn(Optional.of(ol));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of());
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, olId)).thenReturn(false);
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership m = membershipService.addOlMember(olId, userId);

    assertEquals(MembershipRole.OL_MEMBER, m.getRole());
    verify(orgChartService).mirrorOlMember(olId, userId);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_GRANTED), eq(olId), any(), eq(userId), any());
  }

  @Test
  void addOlMember_duplicate_throws() {
    UUID olId = UUID.randomUUID();
    Organisationsleitung ol = new Organisationsleitung();
    ol.setId(olId);
    when(orgUnitRepository.findById(olId)).thenReturn(Optional.of(ol));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(membershipRepository.findAllByIdUserIdAndKind(userId, OrgUnitKind.SQUADRON))
        .thenReturn(List.of());
    when(membershipRepository.existsByIdUserIdAndIdOrgUnitId(userId, olId)).thenReturn(true);

    assertThrows(DuplicateEntityException.class, () -> membershipService.addOlMember(olId, userId));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  // --- assign/remove squadron rank (epic #800 Phase 3) ----------------------

  /** A Staffel membership row for {@link #userId} on the given squadron with the given rank. */
  private OrgUnitMembership squadronMember(UUID squadronId, MembershipRole role) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(userId, squadronId));
    m.setKind(OrgUnitKind.SQUADRON);
    m.setRole(role);
    m.setVersion(0L);
    return m;
  }

  @Test
  void assignSquadronRank_staffelleiter_grantsAndAudits() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership m = squadronMember(squadronId, MembershipRole.MEMBER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class))).thenReturn(Optional.of(m));
    when(membershipRepository.findAllByIdOrgUnitId(squadronId)).thenReturn(List.of(m));
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership saved =
        membershipService.assignSquadronRank(
            squadronId, userId, MembershipRole.STAFFELLEITER, null, 0L);

    assertEquals(MembershipRole.STAFFELLEITER, saved.getRole());
    verify(orgChartService)
        .mirrorSquadronRank(eq(squadronId), eq(userId), eq(MembershipRole.STAFFELLEITER), isNull());
    verify(auditService)
        .record(eq(AuditEventType.ROLE_GRANTED), eq(squadronId), any(), eq(userId), any());
  }

  @Test
  void assignSquadronRank_nonSquadronRank_throwsBadRequest() {
    UUID squadronId = UUID.randomUUID();
    assertThrows(
        BadRequestException.class,
        () ->
            membershipService.assignSquadronRank(
                squadronId, userId, MembershipRole.BEREICHSLEITER, null, 0L));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignSquadronRank_notMember_throwsNotFound() {
    UUID squadronId = UUID.randomUUID();
    when(membershipRepository.findById(any(OrgUnitMembershipId.class)))
        .thenReturn(Optional.empty());
    assertThrows(
        NotFoundException.class,
        () ->
            membershipService.assignSquadronRank(
                squadronId, userId, MembershipRole.STAFFELLEITER, null, 0L));
  }

  @Test
  void assignSquadronRank_kommandoleiterWithoutGroup_throwsBadRequest() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership m = squadronMember(squadronId, MembershipRole.MEMBER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class))).thenReturn(Optional.of(m));

    assertThrows(
        BadRequestException.class,
        () ->
            membershipService.assignSquadronRank(
                squadronId, userId, MembershipRole.KOMMANDOLEITER, null, 0L));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  @Test
  void assignSquadronRank_kommandoleiterWithGroup_grants() {
    UUID squadronId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Squadron squadron = new Squadron();
    squadron.setId(squadronId);
    KommandoGroup group =
        KommandoGroup.builder().squadron(squadron).name("Alpha").sortIndex(0).build();
    group.setId(groupId);
    OrgUnitMembership m = squadronMember(squadronId, MembershipRole.MEMBER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class))).thenReturn(Optional.of(m));
    when(kommandoGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(membershipRepository.findAllByIdOrgUnitId(squadronId)).thenReturn(List.of(m));
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership saved =
        membershipService.assignSquadronRank(
            squadronId, userId, MembershipRole.KOMMANDOLEITER, groupId, 0L);

    assertEquals(MembershipRole.KOMMANDOLEITER, saved.getRole());
    assertSame(group, saved.getKommandoGroup());
    verify(orgChartService)
        .mirrorSquadronRank(squadronId, userId, MembershipRole.KOMMANDOLEITER, group);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_GRANTED), eq(squadronId), any(), eq(userId), any());
  }

  @Test
  void assignSquadronRank_secondStaffelleiter_throwsBadRequest() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership target = squadronMember(squadronId, MembershipRole.MEMBER);
    OrgUnitMembership existingLead = new OrgUnitMembership();
    existingLead.setId(new OrgUnitMembershipId(UUID.randomUUID(), squadronId));
    existingLead.setKind(OrgUnitKind.SQUADRON);
    existingLead.setRole(MembershipRole.STAFFELLEITER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class)))
        .thenReturn(Optional.of(target));
    when(membershipRepository.findAllByIdOrgUnitId(squadronId))
        .thenReturn(List.of(target, existingLead));

    assertThrows(
        BadRequestException.class,
        () ->
            membershipService.assignSquadronRank(
                squadronId, userId, MembershipRole.STAFFELLEITER, null, 0L));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  @Test
  void removeSquadronRank_clearsAndAudits() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership m = squadronMember(squadronId, MembershipRole.STAFFELLEITER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class))).thenReturn(Optional.of(m));
    when(membershipRepository.saveAndFlush(any(OrgUnitMembership.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrgUnitMembership saved = membershipService.removeSquadronRank(squadronId, userId, 0L);

    assertEquals(MembershipRole.MEMBER, saved.getRole());
    verify(orgChartService).mirrorRemoveSquadronRank(squadronId, userId);
    verify(auditService)
        .record(eq(AuditEventType.ROLE_REVOKED), eq(squadronId), any(), eq(userId), any());
  }

  @Test
  void removeSquadronRank_noRank_throwsBadRequest() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership m = squadronMember(squadronId, MembershipRole.MEMBER);
    when(membershipRepository.findById(any(OrgUnitMembershipId.class))).thenReturn(Optional.of(m));

    assertThrows(
        BadRequestException.class,
        () -> membershipService.removeSquadronRank(squadronId, userId, 0L));
    verify(membershipRepository, never()).saveAndFlush(any());
  }

  // --- listOptionsForUser ---------------------------------------------------

  @Test
  void listOptionsForUser_noMemberships_returnsEmptyListWithoutOrgUnitLookup() {
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertTrue(options.isEmpty(), "no memberships → empty option list");
    verify(squadronRepository, never()).findById(any());
    verify(specialCommandRepository, never()).findById(any());
  }

  @Test
  void listOptionsForUser_singleStaffel_returnsOneOption() {
    UUID squadronId = UUID.randomUUID();
    OrgUnitMembership row = new OrgUnitMembership();
    row.setId(new OrgUnitMembershipId(userId, squadronId));
    row.setKind(OrgUnitKind.SQUADRON);
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(row));
    Squadron staffel = new Squadron();
    staffel.setId(squadronId);
    staffel.setName("IRIDIUM");
    staffel.setShorthand("IRI");
    when(squadronRepository.findById(squadronId)).thenReturn(Optional.of(staffel));

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertEquals(1, options.size());
    OrgUnitMembershipOptionDto only = options.getFirst();
    assertEquals(squadronId, only.orgUnitId());
    assertEquals("IRIDIUM", only.orgUnitName());
    assertEquals("IRI", only.orgUnitShorthand());
    assertEquals(OrgUnitKind.SQUADRON, only.kind());
  }

  @Test
  void listOptionsForUser_mixedKinds_sortsStaffelFirstThenSkAlphabetical() {
    UUID staffelId = UUID.randomUUID();
    UUID skBravoId = UUID.randomUUID();
    UUID skAlphaId = UUID.randomUUID();

    OrgUnitMembership rowSkBravo = new OrgUnitMembership();
    rowSkBravo.setId(new OrgUnitMembershipId(userId, skBravoId));
    rowSkBravo.setKind(OrgUnitKind.SPECIAL_COMMAND);
    OrgUnitMembership rowStaffel = new OrgUnitMembership();
    rowStaffel.setId(new OrgUnitMembershipId(userId, staffelId));
    rowStaffel.setKind(OrgUnitKind.SQUADRON);
    OrgUnitMembership rowSkAlpha = new OrgUnitMembership();
    rowSkAlpha.setId(new OrgUnitMembershipId(userId, skAlphaId));
    rowSkAlpha.setKind(OrgUnitKind.SPECIAL_COMMAND);
    when(membershipRepository.findAllByIdUserId(userId))
        .thenReturn(List.of(rowSkBravo, rowStaffel, rowSkAlpha));

    Squadron staffel = new Squadron();
    staffel.setId(staffelId);
    staffel.setName("IRIDIUM");
    staffel.setShorthand("IRI");
    when(squadronRepository.findById(staffelId)).thenReturn(Optional.of(staffel));

    SpecialCommand skBravo = new SpecialCommand();
    skBravo.setId(skBravoId);
    skBravo.setName("Bravo");
    skBravo.setShorthand("BRV");
    when(specialCommandRepository.findById(skBravoId)).thenReturn(Optional.of(skBravo));

    SpecialCommand skAlpha = new SpecialCommand();
    skAlpha.setId(skAlphaId);
    skAlpha.setName("Alpha");
    skAlpha.setShorthand("ALF");
    when(specialCommandRepository.findById(skAlphaId)).thenReturn(Optional.of(skAlpha));

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertEquals(3, options.size());
    assertEquals(OrgUnitKind.SQUADRON, options.get(0).kind());
    assertEquals("IRIDIUM", options.get(0).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, options.get(1).kind());
    assertEquals("Alpha", options.get(1).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, options.get(2).kind());
    assertEquals("Bravo", options.get(2).orgUnitName());
  }

  @Test
  void listOptionsForUser_orphanedRow_silentlyDropsTheMembership() {
    UUID orgUnitId = UUID.randomUUID();
    OrgUnitMembership row = new OrgUnitMembership();
    row.setId(new OrgUnitMembershipId(userId, orgUnitId));
    row.setKind(OrgUnitKind.SQUADRON);
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(row));
    when(squadronRepository.findById(orgUnitId)).thenReturn(Optional.empty());

    List<OrgUnitMembershipOptionDto> options = membershipService.listOptionsForUser(userId);

    assertTrue(
        options.isEmpty(),
        "membership row pointing at a deleted Squadron must not crash the picker");
  }

  // --- listPickerOptionsWithDescendants (epic #692 Phase 5 drill-down) -------

  @Test
  void listPickerOptionsWithDescendants_bereichLeader_includesBereichAndDescendantsTopDown() {
    UUID bereichId = UUID.randomUUID();
    UUID staffelId = UUID.randomUUID();
    OrgUnitMembership bereichRow = new OrgUnitMembership();
    bereichRow.setId(new OrgUnitMembershipId(userId, bereichId));
    bereichRow.setKind(OrgUnitKind.BEREICH);
    bereichRow.setRole(MembershipRole.BEREICHSLEITER);
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of(bereichRow));
    when(orgUnitCascadeService.expandWithDescendants(any()))
        .thenReturn(new java.util.LinkedHashSet<>(List.of(bereichId, staffelId)));
    Bereich bereich = new Bereich();
    bereich.setId(bereichId);
    bereich.setName("Profit");
    bereich.setShorthand("PRF");
    Squadron staffel = new Squadron();
    staffel.setId(staffelId);
    staffel.setName("Alpha");
    staffel.setShorthand("ALF");
    // Return unordered to prove the service applies the top-down hierarchy sort itself.
    when(orgUnitRepository.findAllById(any())).thenReturn(List.of(staffel, bereich));

    List<OrgUnitMembershipOptionDto> options =
        membershipService.listPickerOptionsWithDescendants(userId);

    assertEquals(2, options.size());
    // Top-down hierarchy order: Bereich (1) before Staffel (2).
    assertEquals(OrgUnitKind.BEREICH, options.get(0).kind());
    assertEquals(bereichId, options.get(0).orgUnitId());
    assertEquals(OrgUnitKind.SQUADRON, options.get(1).kind());
    assertEquals(staffelId, options.get(1).orgUnitId());
  }

  @Test
  void listPickerOptionsWithDescendants_noMemberships_returnsEmptyWithoutCascade() {
    when(membershipRepository.findAllByIdUserId(userId)).thenReturn(List.of());

    assertTrue(membershipService.listPickerOptionsWithDescendants(userId).isEmpty());
    verify(orgUnitCascadeService, never()).expandWithDescendants(any());
  }

  // --- listAllActiveOptions (R5.d.c Job Order picker) -----------------------

  @Test
  void listAllActiveOptions_emptyCatalog_returnsEmptyList() {
    when(squadronRepository.findAllByActiveTrue()).thenReturn(List.of());
    when(specialCommandRepository.findAllByActiveTrue()).thenReturn(List.of());

    assertTrue(membershipService.listAllActiveOptions().isEmpty());
  }

  @Test
  void listAllActiveOptions_mixed_sortsStaffelFirstThenSkAlphabetical() {
    Squadron iri = new Squadron();
    iri.setId(UUID.randomUUID());
    iri.setName("IRIDIUM");
    iri.setShorthand("IRI");
    Squadron khg = new Squadron();
    khg.setId(UUID.randomUUID());
    khg.setName("KHG");
    khg.setShorthand("KHG");
    when(squadronRepository.findAllByActiveTrue()).thenReturn(List.of(iri, khg));

    SpecialCommand bravo = new SpecialCommand();
    bravo.setId(UUID.randomUUID());
    bravo.setName("Bravo");
    bravo.setShorthand("BRV");
    SpecialCommand alpha = new SpecialCommand();
    alpha.setId(UUID.randomUUID());
    alpha.setName("Alpha");
    alpha.setShorthand("ALF");
    when(specialCommandRepository.findAllByActiveTrue()).thenReturn(List.of(bravo, alpha));

    List<OrgUnitMembershipOptionDto> result = membershipService.listAllActiveOptions();

    assertEquals(4, result.size());
    // Staffeln first (alphabetical), then SKs (alphabetical).
    assertEquals(OrgUnitKind.SQUADRON, result.get(0).kind());
    assertEquals("IRIDIUM", result.get(0).orgUnitName());
    assertEquals(OrgUnitKind.SQUADRON, result.get(1).kind());
    assertEquals("KHG", result.get(1).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, result.get(2).kind());
    assertEquals("Alpha", result.get(2).orgUnitName());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, result.get(3).kind());
    assertEquals("Bravo", result.get(3).orgUnitName());
  }

  @Test
  void listAllActiveOptions_carriesProfitEligibleFlagPerOrgUnit() {
    // The flag lets the anonymous-reachable create form fill both pickers from one fetch:
    // requesting
    // = all options, responsible = the profit-eligible subset. Pin that each option mirrors its own
    // org unit's flag (a profit Squadron and a non-profit SK here).
    Squadron profitSquadron = new Squadron();
    profitSquadron.setId(UUID.randomUUID());
    profitSquadron.setName("IRIDIUM");
    profitSquadron.setShorthand("IRI");
    profitSquadron.setProfitEligible(true);
    when(squadronRepository.findAllByActiveTrue()).thenReturn(List.of(profitSquadron));

    SpecialCommand nonProfitSk = new SpecialCommand();
    nonProfitSk.setId(UUID.randomUUID());
    nonProfitSk.setName("Alpha");
    nonProfitSk.setShorthand("ALF");
    nonProfitSk.setProfitEligible(false);
    when(specialCommandRepository.findAllByActiveTrue()).thenReturn(List.of(nonProfitSk));

    List<OrgUnitMembershipOptionDto> result = membershipService.listAllActiveOptions();

    assertEquals(2, result.size());
    assertEquals(OrgUnitKind.SQUADRON, result.get(0).kind());
    assertEquals(Boolean.TRUE, result.get(0).isProfitEligible());
    assertEquals(OrgUnitKind.SPECIAL_COMMAND, result.get(1).kind());
    assertEquals(Boolean.FALSE, result.get(1).isProfitEligible());
  }
}
