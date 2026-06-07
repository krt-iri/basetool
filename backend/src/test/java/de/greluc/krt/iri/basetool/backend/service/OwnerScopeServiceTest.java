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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link OwnerScopeService}. Inherits the test scenarios that previously
 * lived under {@code SquadronScopeServiceTest} before the R2.c rename — the implementation moved
 * from {@code SquadronScopeService} to {@code OwnerScopeService} but every behavioural invariant
 * stayed the same. Covers the org-unit-context resolution paths (admin via {@code
 * X-Active-Squadron-Id} request header, non-admin via persistent user record), the aggregate-
 * specific access checks for the five staffel-scoped roots, and the Mission cross-staffel-
 * visibility escape clause ({@code is_internal = false}).
 *
 * <p>The thin {@code SquadronScopeService} shim that still carries the legacy class name has its
 * own minimal smoke test ({@link SquadronScopeServiceTest}) verifying that every shim method
 * forwards to this service.
 */
@ExtendWith(MockitoExtension.class)
class OwnerScopeServiceTest {

  @Mock private AuthHelperService authHelper;
  @Mock private SquadronRepository squadronRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private OperationRepository operationRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private OrgUnitMembershipRepository orgUnitMembershipRepository;
  @Mock private de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository orgUnitRepository;

  @Mock
  private de.greluc.krt.iri.basetool.backend.repository.SpecialCommandRepository
      specialCommandRepository;

  @Mock private HttpServletRequest request;

  @InjectMocks private OwnerScopeService service;

  private static final UUID MEMBER_USER_ID = UUID.randomUUID();
  private static final UUID SQUADRON_A_ID = UUID.randomUUID();
  private static final UUID SQUADRON_B_ID = UUID.randomUUID();

  private Squadron squadronA;
  private Squadron squadronB;
  private User memberUserInA;

  @BeforeEach
  void setUp() {
    squadronA = new Squadron();
    squadronA.setId(SQUADRON_A_ID);
    squadronA.setShorthand("ALF");

    squadronB = new Squadron();
    squadronB.setId(SQUADRON_B_ID);
    squadronB.setShorthand("BRV");

    memberUserInA = new User();
    memberUserInA.setId(MEMBER_USER_ID);
    // Post-R9 D3 (V101): the home Staffel is sourced from org_unit_membership only.
  }

  /** Returns a Staffel membership row pointing the given user at the given Squadron. */
  private static OrgUnitMembership staffelMembership(UUID userId, UUID squadronId) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(userId, squadronId));
    m.setKind(OrgUnitKind.SQUADRON);
    return m;
  }

  /** Returns an SK membership row pointing the given user at the given Special Command. */
  private static OrgUnitMembership skMembership(UUID userId, UUID skOrgUnitId) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(userId, skOrgUnitId));
    m.setKind(OrgUnitKind.SPECIAL_COMMAND);
    return m;
  }

  @Nested
  class CurrentSquadronIdTests {

    @Test
    void adminWithoutHeader_returnsEmpty_admin_seesAllSquadrons() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void adminWithActiveSquadronHeader_returnsThatSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      assertEquals(Optional.of(SQUADRON_B_ID), service.currentSquadronId());
    }

    @Test
    void adminWithBlankHeader_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn("");

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void adminWithMalformedHeader_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn("not-a-uuid");

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void nonAdmin_returnsPersistentUserSquadron() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      // Post-R9 D3 (V101): home Staffel via org_unit_membership.
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));

      assertEquals(Optional.of(SQUADRON_A_ID), service.currentSquadronId());
    }

    @Test
    void nonAdmin_anonymousCaller_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.empty());

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void nonAdmin_userWithoutSquadron_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      // No Staffel membership row → empty.
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of());

      assertTrue(service.currentSquadronId().isEmpty());
    }
  }

  @Nested
  class CurrentSquadronTests {

    @Test
    void resolvesEntityFromCurrentSquadronId() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(squadronRepository.findById(SQUADRON_A_ID)).thenReturn(Optional.of(squadronA));

      assertEquals(Optional.of(squadronA), service.currentSquadron());
    }

    @Test
    void adminInAllSquadronsMode_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.currentSquadron().isEmpty());
    }
  }

  @Nested
  class CanSeeSquadronTests {

    @Test
    void adminWithoutSelection_canSeeAnySquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertTrue(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void adminWithSelection_seesOnlySelectedSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_A_ID.toString());

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void member_seesOnlyHomeSquadron() {
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void member_seesOwnStaffelAndEverySkTheyBelongTo() {
      // Detail/edit visibility now mirrors the list scope: a member sees the union of their Staffel
      // and every Spezialkommando membership, not just the home Staffel.
      UUID skId = UUID.randomUUID();
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(
              List.of(
                  staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID),
                  skMembership(MEMBER_USER_ID, skId)));

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID), "own Staffel");
      assertTrue(service.canSeeSquadron(skId), "own SK — the regression this fixes");
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID), "foreign Staffel stays hidden");
    }

    @Test
    void squadronlessSkMember_seesTheirSk() {
      // The reported case: an SK lead with NO Staffel membership. The old home-Staffel-only gate
      // denied them every org unit including their own SK; the membership union now grants the SK.
      UUID skId = UUID.randomUUID();
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(skMembership(MEMBER_USER_ID, skId)));

      assertTrue(service.canSeeSquadron(skId));
      assertFalse(service.canSeeSquadron(SQUADRON_A_ID));
    }

    @Test
    void nonAdminPinnedToOneMembership_seesOnlyThePin() {
      // Mirrors the list scope: pinning one membership narrows detail/edit to that org unit,
      // exactly as currentScopePredicate() narrows the IN-clause to the pinned id.
      UUID skId = UUID.randomUUID();
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(
              List.of(
                  staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID),
                  skMembership(MEMBER_USER_ID, skId)));
      lenient()
          .when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(skId.toString());

      assertTrue(service.canSeeSquadron(skId), "pinned SK is visible");
      assertFalse(service.canSeeSquadron(SQUADRON_A_ID), "the unpinned Staffel is filtered out");
    }

    @Test
    void nonAdminForeignPin_collapsesToMembershipUnion() {
      // A spoofed/stale pin to an org unit the caller is not a member of must NOT grant foreign
      // access; it collapses to the membership union (same defence as currentScopePredicate()).
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      lenient()
          .when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID), "own Staffel still visible");
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID), "foreign pin grants nothing");
    }

    @Test
    void anonymousCaller_seesNothing() {
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.empty());

      assertFalse(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));
    }
  }

  @Nested
  class CanSeeMissionTests {

    @Test
    void memberSeesOwnSquadronMission() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, squadronA, false);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      stubMemberInSquadronA();

      assertTrue(service.canSeeMission(missionId));
    }

    @Test
    void memberSeesPublicMissionOfOtherSquadron_viaIsInternalEscape() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, squadronB, false);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      stubMemberInSquadronA();

      assertTrue(service.canSeeMission(missionId));
    }

    @Test
    void memberRejectsInternalMissionOfOtherSquadron() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, squadronB, true);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      stubMemberInSquadronA();

      assertFalse(service.canSeeMission(missionId));
    }

    @Test
    void ownerlessPublicMission_isVisibleToEveryone() {
      // An ownerless leadership ("Bereichsleitung") mission carries no owning OrgUnit. When
      // public (not internal) it is visible to everyone, anonymous visitors included — the default
      // the create flow stamps for a membershipless leadership owner.
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, null, false);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

      assertTrue(service.canSeeMission(missionId));
    }

    @Test
    void ownerlessInternalMission_isVisibleToMembersOrAbove() {
      // An internal ownerless mission is "internal to the whole organisation": visible to any
      // member-or-above — the membershipless analogue of a Staffel-internal mission being visible
      // to its Staffel.
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, null, true);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      when(authHelper.isMemberOrAbove()).thenReturn(true);

      assertTrue(service.canSeeMission(missionId));
    }

    @Test
    void ownerlessInternalMission_isHiddenFromOutsiders() {
      // A guest / anonymous outsider (isMemberOrAbove() == false) must not see an internal
      // ownerless mission, mirroring how internal Staffel missions stay hidden from outsiders.
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, null, true);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      when(authHelper.isMemberOrAbove()).thenReturn(false);

      assertFalse(service.canSeeMission(missionId));
    }

    @Test
    void unknownMission_returnsFalse() {
      UUID missionId = UUID.randomUUID();
      when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

      assertFalse(service.canSeeMission(missionId));
    }
  }

  @Nested
  class CanEditMissionTests {

    @Test
    void memberCannotEditPublicMissionOfOtherSquadron() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, squadronB, false);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      stubMemberInSquadronA();

      assertFalse(service.canEditMission(missionId));
    }

    @Test
    void memberCanEditOwnSquadronMission() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, squadronA, true);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
      stubMemberInSquadronA();

      assertTrue(service.canEditMission(missionId));
    }

    @Test
    void ownerlessMission_passesPerRowEditCheck() {
      // An ownerless leadership mission has no owning OrgUnit to scope against, so the per-row
      // canEditMission check is a no-op (true). The real write restriction is then
      // MissionSecurityService.canManageMission's role/owner gate (see MissionSecurityServiceTest).
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, null, false);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

      assertTrue(service.canEditMission(missionId));
    }

    @Test
    void unknownMission_returnsFalse() {
      UUID missionId = UUID.randomUUID();
      when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

      assertFalse(service.canEditMission(missionId));
    }
  }

  @Nested
  class CanSeeJobOrderTests {

    @Test
    void skResponsibleOrder_isPublicToProfitEligibleViewer() {
      // An SK-responsible order is the shared central queue: visible to any profit-eligible caller
      // without a squadron-scope check. The member-in-A default is profit-eligible, so the SK
      // short-circuit fires once the viewer gate passes.
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      stubMemberInSquadronA();

      assertTrue(service.canSeeJobOrder(orderId));
    }

    @Test
    void skResponsibleOrder_invisibleToNonProfitMember() {
      // The viewer-side profit gate suppresses even the otherwise-public SK queue for a caller who
      // belongs to no profit-eligible org unit.
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      stubNonProfitMember();

      assertFalse(service.canSeeJobOrder(orderId));
    }

    @Test
    void skResponsibleOrder_visibleToAdmin() {
      // Admins keep system-wide visibility — the profit gate short-circuits to true for them.
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      when(authHelper.isAdmin()).thenReturn(true);

      assertTrue(service.canSeeJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_visibleToMemberOfThatSquadron() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronA)));
      stubMemberInSquadronA();

      assertTrue(service.canSeeJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_invisibleToForeignSquadronMember() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronB)));
      stubMemberInSquadronA();

      assertFalse(service.canSeeJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_visibleToAdminWithoutPin() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronA)));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.canSeeJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_invisibleToAdminPinnedToOtherSquadron() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronA)));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      assertFalse(service.canSeeJobOrder(orderId));
    }

    @Test
    void unknownOrder_returnsFalse() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId)).thenReturn(Optional.empty());

      assertFalse(service.canSeeJobOrder(orderId));
    }
  }

  @Nested
  class CanEditJobOrderTests {

    @Test
    void skResponsibleOrder_isOpenToTheRoleGate_forProfitEligibleCaller() {
      // SK-order edits are governed by the endpoint's LOGISTICIAN+ role gate, not by squadron
      // scope,
      // so this returns true for the SK case for any profit-eligible caller (the member-in-A
      // default
      // is profit-eligible) — letting any profit squadron contribute to the shared queue.
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      stubMemberInSquadronA();

      assertTrue(service.canEditJobOrder(orderId));
    }

    @Test
    void skResponsibleOrder_notEditableByNonProfitMember() {
      // The viewer-side profit gate also blocks edits: a non-profit member cannot act on the SK
      // queue even though the order is otherwise only role-gated.
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      stubNonProfitMember();

      assertFalse(service.canEditJobOrder(orderId));
    }

    @Test
    void skResponsibleOrder_editableByAdmin() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, newSpecialCommand())));
      when(authHelper.isAdmin()).thenReturn(true);

      assertTrue(service.canEditJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_editableByMemberOfThatSquadron() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronA)));
      stubMemberInSquadronA();

      assertTrue(service.canEditJobOrder(orderId));
    }

    @Test
    void squadronResponsibleOrder_notEditableByForeignSquadronMember() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId))
          .thenReturn(Optional.of(jobOrderResponsibleTo(orderId, squadronB)));
      stubMemberInSquadronA();

      assertFalse(service.canEditJobOrder(orderId));
    }

    @Test
    void unknownOrder_returnsFalse() {
      UUID orderId = UUID.randomUUID();
      when(jobOrderRepository.findById(orderId)).thenReturn(Optional.empty());

      assertFalse(service.canEditJobOrder(orderId));
    }
  }

  @Nested
  class CanViewJobOrdersTests {

    @Test
    void admin_canAlwaysView() {
      when(authHelper.isAdmin()).thenReturn(true);

      assertTrue(service.canViewJobOrders());
    }

    @Test
    void memberOfProfitEligibleOrgUnit_canView() {
      stubMemberInSquadronA(); // member-in-A default is profit-eligible (count > 0)

      assertTrue(service.canViewJobOrders());
    }

    @Test
    void memberOfOnlyNonProfitOrgUnit_cannotView() {
      stubNonProfitMember();

      assertFalse(service.canViewJobOrders());
    }

    @Test
    void callerWithNoMembership_cannotView() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID)).thenReturn(List.of());

      assertFalse(service.canViewJobOrders());
    }

    @Test
    void anonymousCaller_cannotView() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.empty());

      assertFalse(service.canViewJobOrders());
    }
  }

  @Nested
  class CanSeeInventoryItemTests {

    @Test
    void memberSeesOwnSquadronInventoryItem() {
      UUID itemId = UUID.randomUUID();
      InventoryItem item = new InventoryItem();
      item.setId(itemId);
      item.setOwningOrgUnit(squadronA);
      when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));
      stubMemberInSquadronA();

      assertTrue(service.canSeeInventoryItem(itemId));
    }

    @Test
    void memberRejectsForeignSquadronInventoryItem() {
      UUID itemId = UUID.randomUUID();
      InventoryItem item = new InventoryItem();
      item.setId(itemId);
      item.setOwningOrgUnit(squadronB);
      when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));
      stubMemberInSquadronA();

      assertFalse(service.canSeeInventoryItem(itemId));
    }

    @Test
    void unknownItem_returnsFalse() {
      UUID itemId = UUID.randomUUID();
      when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.empty());

      assertFalse(service.canSeeInventoryItem(itemId));
    }
  }

  @Nested
  class CanSeeRefineryOrderAndOperationTests {

    @Test
    void memberRejectsForeignSquadronRefineryOrder() {
      UUID orderId = UUID.randomUUID();
      RefineryOrder order = new RefineryOrder();
      order.setId(orderId);
      order.setOwningOrgUnit(squadronB);
      when(refineryOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
      stubMemberInSquadronA();

      assertFalse(service.canSeeRefineryOrder(orderId));
      assertFalse(service.canEditRefineryOrder(orderId));
    }

    @Test
    void memberAcceptsOwnSquadronRefineryOrder() {
      UUID orderId = UUID.randomUUID();
      RefineryOrder order = new RefineryOrder();
      order.setId(orderId);
      order.setOwningOrgUnit(squadronA);
      when(refineryOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
      stubMemberInSquadronA();

      assertTrue(service.canSeeRefineryOrder(orderId));
      assertTrue(service.canEditRefineryOrder(orderId));
    }

    @Test
    void memberRejectsForeignSquadronOperation() {
      UUID opId = UUID.randomUUID();
      Operation op = new Operation();
      op.setId(opId);
      op.setOwningOrgUnit(squadronB);
      when(operationRepository.findById(opId)).thenReturn(Optional.of(op));
      stubMemberInSquadronA();

      assertFalse(service.canSeeOperation(opId));
      assertFalse(service.canEditOperation(opId));
    }

    @Test
    void adminInAllSquadronsMode_seesEveryAggregate() {
      UUID orderId = UUID.randomUUID();
      RefineryOrder order = new RefineryOrder();
      order.setId(orderId);
      order.setOwningOrgUnit(squadronB);
      when(refineryOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.canSeeRefineryOrder(orderId));
      assertTrue(service.canEditRefineryOrder(orderId));
    }
  }

  /**
   * Null-owner gate behaviour for the three ownerless-personal-aggregate roots (ship, refinery
   * order, inventory item). A row with {@code owningOrgUnit == null} is reachable only by its own
   * owning user or by an admin in all-scopes mode — never by a foreign user or a pinned admin. See
   * {@code OwnerScopeService.canAccessOwnerlessPersonalRow}.
   */
  @Nested
  class OwnerlessPersonalAggregateGateTests {

    private Ship ownerlessShip(User owner) {
      Ship ship = new Ship();
      ship.setId(UUID.randomUUID());
      ship.setOwner(owner);
      ship.setOwningOrgUnit(null);
      return ship;
    }

    private RefineryOrder ownerlessRefineryOrder(User owner) {
      RefineryOrder order = new RefineryOrder();
      order.setId(UUID.randomUUID());
      order.setOwner(owner);
      order.setOwningOrgUnit(null);
      return order;
    }

    private InventoryItem ownerlessInventoryItem(User owner) {
      InventoryItem item = new InventoryItem();
      item.setId(UUID.randomUUID());
      item.setUser(owner);
      item.setOwningOrgUnit(null);
      return item;
    }

    @Test
    void owner_seesAndEditsOwnOwnerlessShip() {
      Ship ship = ownerlessShip(memberUserInA);
      when(shipRepository.findById(ship.getId())).thenReturn(Optional.of(ship));
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));

      assertTrue(service.canSeeShip(ship.getId()));
      assertTrue(service.canEditShip(ship.getId()));
    }

    @Test
    void adminInAllScopesMode_seesOwnerlessShip() {
      Ship ship = ownerlessShip(memberUserInA);
      when(shipRepository.findById(ship.getId())).thenReturn(Optional.of(ship));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      assertTrue(service.canSeeShip(ship.getId()));
      assertTrue(service.canEditShip(ship.getId()));
    }

    @Test
    void adminPinnedToSquadron_doesNotSeeOwnerlessShip() {
      // A pinned admin is scoped like any member; an ownerless row has no scope to match, and the
      // pinned admin is not the owner, so access is denied (it would still be reachable by clearing
      // the pin → all-scopes mode).
      Ship ship = ownerlessShip(memberUserInA);
      when(shipRepository.findById(ship.getId())).thenReturn(Optional.of(ship));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_A_ID.toString());
      when(authHelper.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));

      assertFalse(service.canSeeShip(ship.getId()));
      assertFalse(service.canEditShip(ship.getId()));
    }

    @Test
    void owner_seesAndEditsOwnOwnerlessRefineryOrder() {
      RefineryOrder order = ownerlessRefineryOrder(memberUserInA);
      when(refineryOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));

      assertTrue(service.canSeeRefineryOrder(order.getId()));
      assertTrue(service.canEditRefineryOrder(order.getId()));
    }

    @Test
    void foreignUser_cannotSeeOwnerlessRefineryOrder() {
      RefineryOrder order = ownerlessRefineryOrder(memberUserInA);
      when(refineryOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));

      assertFalse(service.canSeeRefineryOrder(order.getId()));
      assertFalse(service.canEditRefineryOrder(order.getId()));
    }

    @Test
    void owner_seesAndEditsOwnOwnerlessInventoryItem() {
      InventoryItem item = ownerlessInventoryItem(memberUserInA);
      when(inventoryItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));

      assertTrue(service.canSeeInventoryItem(item.getId()));
      assertTrue(service.canEditInventoryItem(item.getId()));
    }

    @Test
    void foreignUser_cannotSeeOwnerlessInventoryItem() {
      InventoryItem item = ownerlessInventoryItem(memberUserInA);
      when(inventoryItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));

      assertFalse(service.canSeeInventoryItem(item.getId()));
      assertFalse(service.canEditInventoryItem(item.getId()));
    }
  }

  /**
   * Verifies the request-scoped memoisation on {@link OwnerScopeService#currentSquadronId()} and
   * {@link OwnerScopeService#currentSquadron()}. Without this, every controller call chain on a
   * non-admin request would re-hit the {@code org_unit_membership} lookup and {@code
   * squadronRepository.findById} once per scope query.
   */
  @Nested
  class RequestScopedCacheTests {

    private Map<String, Object> requestAttributes;

    @BeforeEach
    void backRequestAttributesWithMap() {
      requestAttributes = new HashMap<>();
      lenient()
          .when(request.getAttribute(anyString()))
          .thenAnswer(inv -> requestAttributes.get(inv.getArgument(0, String.class)));
      lenient()
          .doAnswer(
              inv -> {
                requestAttributes.put(inv.getArgument(0), inv.getArgument(1));
                return null;
              })
          .when(request)
          .setAttribute(anyString(), any());
    }

    @Test
    void currentSquadronId_nonAdmin_calledTwice_hitsMembershipRepoOnce() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));

      Optional<UUID> first = service.currentSquadronId();
      Optional<UUID> second = service.currentSquadronId();

      assertEquals(Optional.of(SQUADRON_A_ID), first);
      assertEquals(first, second);
      verify(orgUnitMembershipRepository, times(1))
          .findAllByIdUserIdAndKind(MEMBER_USER_ID, OrgUnitKind.SQUADRON);
    }

    @Test
    void currentSquadron_calledTwice_hitsSquadronRepoOnce() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(squadronRepository.findById(SQUADRON_A_ID)).thenReturn(Optional.of(squadronA));

      Optional<Squadron> first = service.currentSquadron();
      Optional<Squadron> second = service.currentSquadron();

      assertEquals(Optional.of(squadronA), first);
      assertEquals(first, second);
      verify(squadronRepository, times(1)).findById(SQUADRON_A_ID);
    }

    @Test
    void canSeeSquadron_calledTwice_hitsMembershipRepoOnce() {
      // canSeeSquadron now evaluates the same scope vector as the list queries
      // (currentScopePredicate). The membership lookup behind it is request-scoped, so repeated
      // per-row detail/edit checks in one request collapse to a single org_unit_membership read.
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));

      verify(orgUnitMembershipRepository, times(1)).findAllByIdUserId(MEMBER_USER_ID);
    }
  }

  // --- resolveSquadronForPickerOutput (R5.d picker shared helper, hardened in R6.b
  //     to enforce the plan §5.5.1 0/1/>1 membership matrix) ----------------------

  @Test
  void resolveSquadronForPickerOutput_singleStaffelOnlyMembership_nullPicker_autoStamps() {
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());
    // Post-R9 D3 (V101): every membership (Staffel + SK) comes from findAllByIdUserId.
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    Squadron result = service.resolveSquadronForPickerOutput(user, null);

    assertSame(homeStaffel, result);
  }

  @Test
  void resolveSquadronForPickerOutput_noMembershipAtAll_throwsBadRequest() {
    // Memberless user (admin / guest / freshly created without a backfill) — must be rejected
    // before the stamp lands. Post-R9 D3 (V101) the legacy Staffel column is dropped, so the
    // membership-table emptiness is the single criterion.
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId())).thenReturn(List.of());

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> service.resolveSquadronForPickerOutput(user, null));
    assertTrue(ex.getMessage().toLowerCase().contains("no org-unit membership"), ex.getMessage());
    verify(squadronRepository, never()).findById(any());
  }

  @Test
  void resolveSquadronForPickerOutput_multipleMemberships_nullPicker_throwsBadRequest() {
    // User in Staffel + at least one SK. Plan §5.5.1: ambiguous, must reject. Before R6.b
    // this path silently stamped the legacy Staffel — exactly the audit regression #4.
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(
            List.of(
                staffelMembership(user.getId(), homeStaffelId),
                skMembership(user.getId(), UUID.randomUUID())));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> service.resolveSquadronForPickerOutput(user, null));
    assertTrue(
        ex.getMessage().toLowerCase().contains("owningorgunitid is required"), ex.getMessage());
    verify(squadronRepository, never()).findById(any());
  }

  @Test
  void resolveSquadronForPickerOutput_validStaffelPick_returnsPickedSquadron() {
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    Squadron result = service.resolveSquadronForPickerOutput(user, homeStaffelId);

    assertSame(homeStaffel, result, "the picked Staffel must be returned verbatim");
  }

  @Test
  void resolveSquadronForPickerOutput_validMultiMembershipStaffelPick_returnsPickedSquadron() {
    // User has Staffel + SK; picker output points at the Staffel. Honoured.
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());

    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(
            List.of(
                staffelMembership(user.getId(), homeStaffelId),
                skMembership(user.getId(), UUID.randomUUID())));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    Squadron result = service.resolveSquadronForPickerOutput(user, homeStaffelId);

    assertSame(homeStaffel, result);
  }

  @Test
  void resolveSquadronForPickerOutput_foreignOrgUnitChoice_throwsBadRequest() {
    // Picker output references an OrgUnit the target user does NOT belong to (membership
    // forgery vector).
    UUID homeStaffelId = UUID.randomUUID();
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));
    UUID foreignId = UUID.randomUUID();

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> service.resolveSquadronForPickerOutput(user, foreignId));
    assertTrue(ex.getMessage().toLowerCase().contains("not a membership"), ex.getMessage());
    verify(squadronRepository, never()).findById(any());
  }

  @Test
  void resolveSquadronForPickerOutput_pickedOrgUnitIsSpecialCommand_throwsBadRequest() {
    // The user has Staffel + SK; the picker points at the SK. SquadronRepository.findById
    // returns empty for an SK id (the JPA single-table discriminator filter limits the repo
    // to kind='SQUADRON' rows), so the soft block fires.
    UUID homeStaffelId = UUID.randomUUID();
    User user = new User();
    user.setId(UUID.randomUUID());

    UUID skId = UUID.randomUUID();
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(
            List.of(
                staffelMembership(user.getId(), homeStaffelId), skMembership(user.getId(), skId)));
    when(squadronRepository.findById(skId)).thenReturn(Optional.empty());

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> service.resolveSquadronForPickerOutput(user, skId));
    assertTrue(
        ex.getMessage().toLowerCase().contains("spezialkommando ownership"), ex.getMessage());
  }

  // --- resolveOrgUnitForPickerOutput (V99-aligned SK-unblocking successor of
  // resolveSquadronForPickerOutput; honours SK selections once V99 lifts the legacy NOT NULL) ---

  @Test
  void resolveOrgUnitForPickerOutput_singleStaffelOnlyMembership_nullPicker_returnsStaffel() {
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    de.greluc.krt.iri.basetool.backend.model.OrgUnit result =
        service.resolveOrgUnitForPickerOutput(user, null);

    assertSame(homeStaffel, result);
    // SK repo not consulted on the Staffel branch.
    verify(specialCommandRepository, never()).findById(any());
  }

  @Test
  void resolveOrgUnitForPickerOutput_pickedSpecialCommand_isHonoured() {
    // V99 unblocks SK ownership: the new method now returns the SpecialCommand entity instead
    // of throwing "not yet supported". The legacy resolver still rejects, which proves the
    // dual-track is clean.
    UUID homeStaffelId = UUID.randomUUID();
    User user = new User();
    user.setId(UUID.randomUUID());

    UUID skId = UUID.randomUUID();
    de.greluc.krt.iri.basetool.backend.model.SpecialCommand sk =
        new de.greluc.krt.iri.basetool.backend.model.SpecialCommand();
    sk.setId(skId);
    sk.setName("Alpha");

    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(
            List.of(
                staffelMembership(user.getId(), homeStaffelId), skMembership(user.getId(), skId)));
    when(squadronRepository.findById(skId)).thenReturn(Optional.empty());
    when(specialCommandRepository.findById(skId)).thenReturn(Optional.of(sk));

    de.greluc.krt.iri.basetool.backend.model.OrgUnit result =
        service.resolveOrgUnitForPickerOutput(user, skId);

    assertSame(sk, result);
  }

  @Test
  void resolveOrgUnitForPickerOutput_foreignOrgUnitChoice_throwsBadRequest() {
    UUID homeStaffelId = UUID.randomUUID();
    User user = new User();
    user.setId(UUID.randomUUID());
    UUID foreignId = UUID.randomUUID();
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> service.resolveOrgUnitForPickerOutput(user, foreignId));
    assertTrue(ex.getMessage().toLowerCase().contains("not a membership"), ex.getMessage());
  }

  @Test
  void resolveOrgUnitForPickerOutput_noMembershipAtAll_throwsBadRequest() {
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId())).thenReturn(List.of());

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> service.resolveOrgUnitForPickerOutput(user, null));
    assertTrue(ex.getMessage().toLowerCase().contains("no org-unit membership"), ex.getMessage());
  }

  // --- resolveOrgUnitForPickerOutputNullable (ownerless-personal-aggregate variant: a
  // membershipless user with no explicit picker output resolves to null instead of a 400; every
  // other matrix branch is delegated to the same shared tail as the strict resolver) ---

  @Test
  void resolveOrgUnitForPickerOutputNullable_noMembership_nullPicker_returnsNull() {
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId())).thenReturn(List.of());

    assertNull(service.resolveOrgUnitForPickerOutputNullable(user, null));
  }

  @Test
  void resolveOrgUnitForPickerOutputNullable_noMembership_withPicker_throwsBadRequest() {
    // A membershipless user cannot claim ownership of an org unit they do not belong to — a
    // non-null picker output is still a foreign-org-unit forgery, even though the null-picker case
    // is now allowed (ownerless).
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId())).thenReturn(List.of());

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> service.resolveOrgUnitForPickerOutputNullable(user, UUID.randomUUID()));
    assertTrue(ex.getMessage().toLowerCase().contains("not a membership"), ex.getMessage());
  }

  @Test
  void resolveOrgUnitForPickerOutputNullable_singleMembership_nullPicker_autoStamps() {
    Squadron homeStaffel = new Squadron();
    UUID homeStaffelId = UUID.randomUUID();
    homeStaffel.setId(homeStaffelId);
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    assertSame(homeStaffel, service.resolveOrgUnitForPickerOutputNullable(user, null));
  }

  @Test
  void resolveOrgUnitForPickerOutputNullable_foreignChoice_throwsBadRequest() {
    User user = new User();
    user.setId(UUID.randomUUID());
    UUID homeStaffelId = UUID.randomUUID();
    when(orgUnitMembershipRepository.findAllByIdUserId(user.getId()))
        .thenReturn(List.of(staffelMembership(user.getId(), homeStaffelId)));

    assertThrows(
        BadRequestException.class,
        () -> service.resolveOrgUnitForPickerOutputNullable(user, UUID.randomUUID()));
  }

  @Nested
  class HasRoleInOrgUnitTests {

    @Test
    void admin_alwaysReturnsTrue_evenWithoutAnyAuthorities() {
      UUID orgUnit = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(true);
      assertTrue(service.hasRoleInOrgUnit(orgUnit, "LOGISTICIAN"));
    }

    @Test
    void anonymousAuthentication_returnsFalse() {
      UUID orgUnit = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentAuthentication()).thenReturn(Optional.empty());
      assertFalse(service.hasRoleInOrgUnit(orgUnit, "LOGISTICIAN"));
    }

    @Test
    void matchingContextualAuthority_returnsTrue() {
      UUID orgUnit = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(false);
      org.springframework.security.core.GrantedAuthority granted =
          new de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority(
              "LOGISTICIAN", orgUnit);
      withAuthorities(java.util.List.of(granted));
      assertTrue(service.hasRoleInOrgUnit(orgUnit, "LOGISTICIAN"));
    }

    @Test
    void contextualAuthorityForDifferentOrgUnit_returnsFalse() {
      UUID orgUnitA = UUID.randomUUID();
      UUID orgUnitB = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(false);
      org.springframework.security.core.GrantedAuthority granted =
          new de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority(
              "LOGISTICIAN", orgUnitA);
      withAuthorities(java.util.List.of(granted));
      assertFalse(service.hasRoleInOrgUnit(orgUnitB, "LOGISTICIAN"));
    }

    @Test
    void contextualAuthorityForDifferentRole_returnsFalse() {
      UUID orgUnit = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(false);
      org.springframework.security.core.GrantedAuthority granted =
          new de.greluc.krt.iri.basetool.backend.config.OrgUnitContextualAuthority(
              "MISSION_MANAGER", orgUnit);
      withAuthorities(java.util.List.of(granted));
      assertFalse(service.hasRoleInOrgUnit(orgUnit, "LOGISTICIAN"));
    }

    @Test
    void onlyFlatAuthorityNoContextual_returnsFalse() {
      // The flat ROLE_LOGISTICIAN authority is intentionally NOT matched — that's the
      // back-compat surface for hasRole('LOGISTICIAN') gates. The contextual helper requires
      // the contextual authority explicitly.
      UUID orgUnit = UUID.randomUUID();
      when(authHelper.isAdmin()).thenReturn(false);
      org.springframework.security.core.GrantedAuthority flat =
          new org.springframework.security.core.authority.SimpleGrantedAuthority(
              "ROLE_LOGISTICIAN");
      withAuthorities(java.util.List.of(flat));
      assertFalse(service.hasRoleInOrgUnit(orgUnit, "LOGISTICIAN"));
    }

    private void withAuthorities(
        java.util.List<? extends org.springframework.security.core.GrantedAuthority> auths) {
      org.springframework.security.core.Authentication authentication =
          new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
              "user", "n/a", auths);
      when(authHelper.currentAuthentication()).thenReturn(Optional.of(authentication));
    }
  }

  @Nested
  class CanAccessBlueprintOverviewTests {

    @Test
    void admin_canAccess() {
      when(authHelper.isAdmin()).thenReturn(true);

      assertTrue(service.canAccessBlueprintOverview());
    }

    @Test
    void officer_canAccess() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(true);

      assertTrue(service.canAccessBlueprintOverview());
    }

    @Test
    void skLead_canAccess() {
      UUID skId = UUID.randomUUID();
      OrgUnitMembership lead = skMembership(MEMBER_USER_ID, skId);
      lead.setLead(true);
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID), lead));

      assertTrue(service.canAccessBlueprintOverview());
    }

    @Test
    void logisticianFlagWithoutOfficerOrLead_isDenied() {
      // is_logistician alone does NOT grant the overview — only officer / admin / SK-lead do.
      OrgUnitMembership logisticianStaffel = staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID);
      logisticianStaffel.setLogistician(true);
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(logisticianStaffel));

      assertFalse(service.canAccessBlueprintOverview());
    }

    @Test
    void anonymous_isDenied() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.empty());

      assertFalse(service.canAccessBlueprintOverview());
    }
  }

  @Nested
  class CurrentBlueprintOversightScopeTests {

    @Test
    void adminWithoutPin_allScope() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertTrue(scope.adminAllScope());
      assertNull(scope.activeOrgUnitId());
      assertTrue(scope.memberOrgUnitIds().isEmpty());
    }

    @Test
    void adminWithPin_scopesToPinnedOrgUnit() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertFalse(scope.adminAllScope());
      assertEquals(SQUADRON_B_ID, scope.activeOrgUnitId());
      assertTrue(scope.memberOrgUnitIds().isEmpty());
    }

    @Test
    void officer_scopesToOwnStaffel() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(true);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertFalse(scope.adminAllScope());
      assertNull(scope.activeOrgUnitId());
      assertEquals(Set.of(SQUADRON_A_ID), scope.memberOrgUnitIds());
    }

    @Test
    void skLeadOnly_scopesToLedSkNotOwnStaffel() {
      UUID skId = UUID.randomUUID();
      OrgUnitMembership lead = skMembership(MEMBER_USER_ID, skId);
      lead.setLead(true);
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID), lead));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      ScopePredicate scope = service.currentBlueprintOversightScope();

      // Their Staffel is NOT in scope (they are not an officer there); only the led SK is.
      assertEquals(Set.of(skId), scope.memberOrgUnitIds());
    }

    @Test
    void officerWhoAlsoLeadsSk_scopesToBoth() {
      UUID skId = UUID.randomUUID();
      OrgUnitMembership lead = skMembership(MEMBER_USER_ID, skId);
      lead.setLead(true);
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(true);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID), lead));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertEquals(Set.of(SQUADRON_A_ID, skId), scope.memberOrgUnitIds());
    }

    @Test
    void pinWithinOversight_isHonoured() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(true);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_A_ID.toString());

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertEquals(SQUADRON_A_ID, scope.activeOrgUnitId());
      assertTrue(scope.memberOrgUnitIds().isEmpty());
    }

    @Test
    void pinOutsideOversight_isIgnored() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(true);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
              MEMBER_USER_ID, OrgUnitKind.SQUADRON))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertNull(scope.activeOrgUnitId());
      assertEquals(Set.of(SQUADRON_A_ID), scope.memberOrgUnitIds());
    }

    @Test
    void plainMember_emptyOversight() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.hasReachableRole("ROLE_OFFICER")).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
          .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
      when(request.getHeader(OwnerScopeService.ACTIVE_ORG_UNIT_HEADER)).thenReturn(null);

      ScopePredicate scope = service.currentBlueprintOversightScope();

      assertFalse(scope.adminAllScope());
      assertNull(scope.activeOrgUnitId());
      assertTrue(scope.memberOrgUnitIds().isEmpty());
    }
  }

  // --- helpers ------------------------------------------------------------------

  private Mission newMission(UUID id, Squadron owningSquadron, boolean isInternal) {
    Mission mission = new Mission();
    mission.setId(id);
    mission.setOwningOrgUnit(owningSquadron);
    mission.setIsInternal(isInternal);
    return mission;
  }

  /** Builds a {@link JobOrder} responsible to the given org unit (Squadron or SpecialCommand). */
  private static de.greluc.krt.iri.basetool.backend.model.JobOrder jobOrderResponsibleTo(
      UUID id, de.greluc.krt.iri.basetool.backend.model.OrgUnit responsible) {
    de.greluc.krt.iri.basetool.backend.model.JobOrder o =
        new de.greluc.krt.iri.basetool.backend.model.JobOrder();
    o.setId(id);
    o.setResponsibleOrgUnit(responsible);
    return o;
  }

  /** Builds a {@link SpecialCommand} with a random id, used as an SK-responsible org unit. */
  private static SpecialCommand newSpecialCommand() {
    SpecialCommand sc = new SpecialCommand();
    sc.setId(UUID.randomUUID());
    sc.setShorthand("SKX");
    return sc;
  }

  private void stubMemberInSquadronA() {
    lenient().when(authHelper.isAdmin()).thenReturn(false);
    lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
    lenient()
        .when(
            orgUnitMembershipRepository.findAllByIdUserIdAndKind(
                MEMBER_USER_ID, OrgUnitKind.SQUADRON))
        .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
    // canViewJobOrders() reads the all-kinds membership union and the profit-eligibility count.
    // The default member-in-A is treated as profit-eligible so the existing canSeeJobOrder member
    // scenarios still pass the viewer gate; the non-profit case is stubbed explicitly per test.
    lenient()
        .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
        .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
    lenient().when(orgUnitRepository.countProfitEligibleByIdIn(any())).thenReturn(1L);
  }

  /**
   * Stubs a non-admin caller whose single membership is a non-profit-eligible org unit — the viewer
   * gate {@link OwnerScopeService#canViewJobOrders()} must return {@code false} for such a caller.
   */
  private void stubNonProfitMember() {
    lenient().when(authHelper.isAdmin()).thenReturn(false);
    lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
    lenient()
        .when(orgUnitMembershipRepository.findAllByIdUserId(MEMBER_USER_ID))
        .thenReturn(List.of(staffelMembership(MEMBER_USER_ID, SQUADRON_A_ID)));
    lenient().when(orgUnitRepository.countProfitEligibleByIdIn(any())).thenReturn(0L);
  }
}
