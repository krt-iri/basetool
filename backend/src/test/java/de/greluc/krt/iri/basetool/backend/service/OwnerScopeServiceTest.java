package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  @Mock private UserRepository userRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private OperationRepository operationRepository;
  @Mock private OrgUnitMembershipRepository orgUnitMembershipRepository;
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
    memberUserInA.setSquadron(squadronA);
  }

  @Nested
  class CurrentSquadronIdTests {

    @Test
    void adminWithoutHeader_returnsEmpty_admin_seesAllSquadrons() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn(null);

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void adminWithActiveSquadronHeader_returnsThatSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER))
          .thenReturn(SQUADRON_B_ID.toString());

      assertEquals(Optional.of(SQUADRON_B_ID), service.currentSquadronId());
    }

    @Test
    void adminWithBlankHeader_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn("");

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void adminWithMalformedHeader_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn("not-a-uuid");

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void nonAdmin_returnsPersistentUserSquadron() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));

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
      User squadronlessUser = new User();
      squadronlessUser.setId(MEMBER_USER_ID);
      squadronlessUser.setSquadron(null);

      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(squadronlessUser));

      assertTrue(service.currentSquadronId().isEmpty());
    }
  }

  @Nested
  class CurrentSquadronTests {

    @Test
    void resolvesEntityFromCurrentSquadronId() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));
      when(squadronRepository.findById(SQUADRON_A_ID)).thenReturn(Optional.of(squadronA));

      assertEquals(Optional.of(squadronA), service.currentSquadron());
    }

    @Test
    void adminInAllSquadronsMode_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn(null);

      assertTrue(service.currentSquadron().isEmpty());
    }
  }

  @Nested
  class CanSeeSquadronTests {

    @Test
    void adminWithoutSelection_canSeeAnySquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn(null);

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertTrue(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void adminWithSelection_seesOnlySelectedSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER))
          .thenReturn(SQUADRON_A_ID.toString());

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void member_seesOnlyHomeSquadron() {
      lenient().when(authHelper.isAdmin()).thenReturn(false);
      lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      lenient()
          .when(userRepository.findById(MEMBER_USER_ID))
          .thenReturn(Optional.of(memberUserInA));

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));
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
    void missionWithNullOwningSquadron_isVisibleToEveryone() {
      UUID missionId = UUID.randomUUID();
      Mission mission = newMission(missionId, null, true);
      when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

      assertTrue(service.canSeeMission(missionId));
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
    void unknownMission_returnsFalse() {
      UUID missionId = UUID.randomUUID();
      when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

      assertFalse(service.canEditMission(missionId));
    }
  }

  @Nested
  class CanSeeInventoryItemTests {

    @Test
    void memberSeesOwnSquadronInventoryItem() {
      UUID itemId = UUID.randomUUID();
      InventoryItem item = new InventoryItem();
      item.setId(itemId);
      item.setOwningSquadron(squadronA);
      when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));
      stubMemberInSquadronA();

      assertTrue(service.canSeeInventoryItem(itemId));
    }

    @Test
    void memberRejectsForeignSquadronInventoryItem() {
      UUID itemId = UUID.randomUUID();
      InventoryItem item = new InventoryItem();
      item.setId(itemId);
      item.setOwningSquadron(squadronB);
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
      order.setOwningSquadron(squadronB);
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
      order.setOwningSquadron(squadronA);
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
      op.setOwningSquadron(squadronB);
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
      order.setOwningSquadron(squadronB);
      when(refineryOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getHeader(OwnerScopeService.ACTIVE_SQUADRON_HEADER)).thenReturn(null);

      assertTrue(service.canSeeRefineryOrder(orderId));
      assertTrue(service.canEditRefineryOrder(orderId));
    }
  }

  /**
   * Verifies the request-scoped memoisation on {@link OwnerScopeService#currentSquadronId()} and
   * {@link OwnerScopeService#currentSquadron()}. Without this, every controller call chain on a
   * non-admin request would re-hit {@code userRepository.findById} and {@code
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
    void currentSquadronId_nonAdmin_calledTwice_hitsUserRepoOnce() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));

      Optional<UUID> first = service.currentSquadronId();
      Optional<UUID> second = service.currentSquadronId();

      assertEquals(Optional.of(SQUADRON_A_ID), first);
      assertEquals(first, second);
      verify(userRepository, times(1)).findById(MEMBER_USER_ID);
    }

    @Test
    void currentSquadron_calledTwice_hitsSquadronRepoOnce() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));
      when(squadronRepository.findById(SQUADRON_A_ID)).thenReturn(Optional.of(squadronA));

      Optional<Squadron> first = service.currentSquadron();
      Optional<Squadron> second = service.currentSquadron();

      assertEquals(Optional.of(squadronA), first);
      assertEquals(first, second);
      verify(squadronRepository, times(1)).findById(SQUADRON_A_ID);
    }

    @Test
    void currentSquadronId_andCanSeeSquadron_shareUserRepoLookup() {
      when(authHelper.isAdmin()).thenReturn(false);
      when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
      when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));

      service.currentSquadronId();
      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertFalse(service.canSeeSquadron(SQUADRON_B_ID));

      verify(userRepository, times(1)).findById(MEMBER_USER_ID);
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
    user.setSquadron(homeStaffel);
    // No SK memberships — single-Staffel user, picker is hidden in the UI so {@code null}
    // is the only realistic owner choice.
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of());
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    Squadron result = service.resolveSquadronForPickerOutput(user, null);

    assertSame(homeStaffel, result);
  }

  @Test
  void resolveSquadronForPickerOutput_noMembershipAtAll_throwsBadRequest() {
    // Memberless user (admin / guest / freshly created without a backfill) — must be rejected
    // before the stamp lands. Today this state is impossible (app_user.squadron_id is NOT NULL
    // and V95 backfilled every existing row), but the guard is defensive against the post-D3
    // world where the legacy Staffel column is dropped.
    User user = new User();
    user.setId(UUID.randomUUID());
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of());

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
    homeStaffel.setId(UUID.randomUUID());
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setSquadron(homeStaffel);

    OrgUnitMembership skMembership = new OrgUnitMembership();
    OrgUnitMembershipId skId = new OrgUnitMembershipId(user.getId(), UUID.randomUUID());
    skMembership.setId(skId);
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of(skMembership));

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
    user.setSquadron(homeStaffel);
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of());
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
    user.setSquadron(homeStaffel);

    OrgUnitMembership skMembership = new OrgUnitMembership();
    skMembership.setId(new OrgUnitMembershipId(user.getId(), UUID.randomUUID()));
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of(skMembership));
    when(squadronRepository.findById(homeStaffelId)).thenReturn(Optional.of(homeStaffel));

    Squadron result = service.resolveSquadronForPickerOutput(user, homeStaffelId);

    assertSame(homeStaffel, result);
  }

  @Test
  void resolveSquadronForPickerOutput_foreignOrgUnitChoice_throwsBadRequest() {
    // Picker output references an OrgUnit the target user does NOT belong to (membership
    // forgery vector).
    Squadron homeStaffel = new Squadron();
    homeStaffel.setId(UUID.randomUUID());
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setSquadron(homeStaffel);
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of());
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
    Squadron homeStaffel = new Squadron();
    homeStaffel.setId(UUID.randomUUID());
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setSquadron(homeStaffel);

    UUID skId = UUID.randomUUID();
    OrgUnitMembership skMembership = new OrgUnitMembership();
    skMembership.setId(new OrgUnitMembershipId(user.getId(), skId));
    when(orgUnitMembershipRepository.findAllByIdUserIdAndKind(
            user.getId(), OrgUnitKind.SPECIAL_COMMAND))
        .thenReturn(List.of(skMembership));
    when(squadronRepository.findById(skId)).thenReturn(Optional.empty());

    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> service.resolveSquadronForPickerOutput(user, skId));
    assertTrue(
        ex.getMessage().toLowerCase().contains("spezialkommando ownership"), ex.getMessage());
  }

  // --- helpers ------------------------------------------------------------------

  private Mission newMission(UUID id, Squadron owningSquadron, boolean isInternal) {
    Mission mission = new Mission();
    mission.setId(id);
    mission.setOwningSquadron(owningSquadron);
    mission.setIsInternal(isInternal);
    return mission;
  }

  private void stubMemberInSquadronA() {
    lenient().when(authHelper.isAdmin()).thenReturn(false);
    lenient().when(authHelper.currentUserId()).thenReturn(Optional.of(MEMBER_USER_ID));
    lenient().when(userRepository.findById(MEMBER_USER_ID)).thenReturn(Optional.of(memberUserInA));
  }
}
