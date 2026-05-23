package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipDeltaRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipDeltaRequest.SpecialCommandChange;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipDeltaRequest.SpecialCommandChange.Action;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipDeltaRequest.StaffelChange;
import de.greluc.krt.iri.basetool.backend.model.dto.MembershipFlagsPatchRequest;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link UserService#applyMembershipDelta} — the SPEZIALKOMMANDO_PLAN.md §7.4 single-POST
 * membership-delta orchestrator. Verifies:
 *
 * <ul>
 *   <li>Staffel reassignment routes through {@code updateUserSquadron} (which keeps the membership
 *       row in lockstep), then applies any explicit flag patch on top.
 *   <li>Staffel flag-only delta (squadronId unchanged) bypasses {@code updateUserSquadron} and hits
 *       {@code applyStaffelMembershipFlagDelta} directly.
 *   <li>SK ADD calls {@link OrgUnitMembershipService#addMember} and adopts initial flags inline via
 *       dirty-checking on the returned row (no second explicit save).
 *   <li>SK REMOVE calls {@link OrgUnitMembershipService#removeMember}.
 *   <li>SK PATCH wraps the change in a {@link MembershipFlagsPatchRequest} that carries the per-row
 *       version and delegates to {@link OrgUnitMembershipService#patchFlags}.
 *   <li>Unknown user surfaces as 404 immediately, before any change is applied.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceMembershipDeltaTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private AuthHelperService authHelperService;
  @Mock private OwnerScopeService ownerScopeService;
  @Mock private OrgUnitMembershipService orgUnitMembershipService;

  @InjectMocks private UserService userService;

  @Test
  void unknownUser_throwsNoSuchElement_andNothingFires() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(
        NoSuchElementException.class,
        () -> userService.applyMembershipDelta(userId, new MembershipDeltaRequest(null, null)));
    verify(orgUnitMembershipService, never()).addMember(any(), any());
    verify(orgUnitMembershipService, never()).removeMember(any(), any());
    verify(orgUnitMembershipService, never()).patchFlags(any(), any(), any());
    verify(orgUnitMembershipService, never()).applyStaffelMembershipFlagDelta(any(), any(), any());
  }

  @Test
  void emptyDelta_returnsCurrentStateWithoutMutating() {
    UUID userId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());

    List<OrgUnitMembership> result =
        userService.applyMembershipDelta(userId, new MembershipDeltaRequest(null, null));

    assertEquals(0, result.size());
    verify(orgUnitMembershipService, never()).addMember(any(), any());
    verify(orgUnitMembershipService, never()).removeMember(any(), any());
    verify(orgUnitMembershipService, never()).patchFlags(any(), any(), any());
    verify(orgUnitMembershipService, never()).applyStaffelMembershipFlagDelta(any(), any(), any());
  }

  @Test
  void staffelReassignment_routesThroughUpdateUserSquadron_thenFlagDelta() {
    UUID userId = UUID.randomUUID();
    UUID oldSquadronId = UUID.randomUUID();
    UUID newSquadronId = UUID.randomUUID();
    Squadron newSquadron = new Squadron();
    newSquadron.setId(newSquadronId);
    User user = newUser(userId);
    user.setVersion(7L);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(squadronRepository.findById(newSquadronId)).thenReturn(Optional.of(newSquadron));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());
    // Post-R9 D3 (V101): the current Staffel is read from org_unit_membership.
    when(orgUnitMembershipService.findStaffelMembershipOrgUnitId(userId))
        .thenReturn(Optional.of(oldSquadronId));

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(new StaffelChange(newSquadronId, true, false, 7L), null);
    userService.applyMembershipDelta(userId, delta);

    // updateUserSquadron is called inside applyMembershipDelta — which in turn calls
    // OrgUnitMembershipService.syncStaffelMembership. After that, the explicit flag patch fires.
    verify(orgUnitMembershipService).syncStaffelMembership(any(User.class), eq(newSquadron));
    verify(orgUnitMembershipService).applyStaffelMembershipFlagDelta(userId, true, false);
  }

  @Test
  void staffelFlagOnlyDelta_bypassesUpdateUserSquadron() {
    UUID userId = UUID.randomUUID();
    UUID squadronId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());
    when(orgUnitMembershipService.findStaffelMembershipOrgUnitId(userId))
        .thenReturn(Optional.of(squadronId));

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(
            new StaffelChange(squadronId, /* isLogistician */ true, null, /* version */ null),
            null);
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService, never()).syncStaffelMembership(any(), any());
    verify(orgUnitMembershipService).applyStaffelMembershipFlagDelta(userId, true, null);
  }

  @Test
  void staffelClear_routesThroughUpdateUserSquadronWithNull() {
    UUID userId = UUID.randomUUID();
    UUID oldSquadronId = UUID.randomUUID();
    User user = newUser(userId);
    user.setVersion(3L);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());
    when(orgUnitMembershipService.findStaffelMembershipOrgUnitId(userId))
        .thenReturn(Optional.of(oldSquadronId));

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(new StaffelChange(null, null, null, 3L), null);
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService).syncStaffelMembership(any(User.class), isNull());
    verify(orgUnitMembershipService, never()).applyStaffelMembershipFlagDelta(any(), any(), any());
  }

  @Test
  void skAdd_callsAddMember_andAdoptsInitialFlagsInline() {
    UUID userId = UUID.randomUUID();
    UUID skId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    OrgUnitMembership freshRow = new OrgUnitMembership();
    when(orgUnitMembershipService.addMember(skId, userId)).thenReturn(freshRow);
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of(freshRow));

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(
            null, List.of(new SpecialCommandChange(skId, Action.ADD, true, true, null)));
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService).addMember(skId, userId);
    // Initial flags set inline on the managed entity (no second save call needed).
    assertEquals(true, freshRow.isLogistician());
    assertEquals(true, freshRow.isMissionManager());
  }

  @Test
  void skAdd_withoutFlags_doesNotMutateRow() {
    UUID userId = UUID.randomUUID();
    UUID skId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    OrgUnitMembership freshRow = new OrgUnitMembership();
    when(orgUnitMembershipService.addMember(skId, userId)).thenReturn(freshRow);
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of(freshRow));

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(
            null, List.of(new SpecialCommandChange(skId, Action.ADD, null, null, null)));
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService).addMember(skId, userId);
    assertEquals(false, freshRow.isLogistician());
    assertEquals(false, freshRow.isMissionManager());
  }

  @Test
  void skRemove_callsRemoveMember() {
    UUID userId = UUID.randomUUID();
    UUID skId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(
            null, List.of(new SpecialCommandChange(skId, Action.REMOVE, null, null, null)));
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService).removeMember(skId, userId);
    verify(orgUnitMembershipService, never()).addMember(any(), any());
    verify(orgUnitMembershipService, never()).patchFlags(any(), any(), any());
  }

  @Test
  void skPatch_callsPatchFlags_withVersionFromChangeRecord() {
    UUID userId = UUID.randomUUID();
    UUID skId = UUID.randomUUID();
    User user = newUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(orgUnitMembershipService.findAllMembershipsForUser(userId)).thenReturn(List.of());

    MembershipDeltaRequest delta =
        new MembershipDeltaRequest(
            null, List.of(new SpecialCommandChange(skId, Action.PATCH, true, false, 4L)));
    userService.applyMembershipDelta(userId, delta);

    verify(orgUnitMembershipService)
        .patchFlags(eq(skId), eq(userId), eq(new MembershipFlagsPatchRequest(true, false, 4L)));
  }

  private User newUser(UUID id) {
    User user = new User();
    user.setId(id);
    user.setVersion(0L);
    return user;
  }
}
