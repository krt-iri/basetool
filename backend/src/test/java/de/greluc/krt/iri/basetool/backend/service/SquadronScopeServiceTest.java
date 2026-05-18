package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
 * Mockito unit tests for {@link SquadronScopeService}. Covers the squadron-context resolution paths
 * (admin via session, non-admin via persistent user record), the aggregate-specific access checks
 * for the five staffel-scoped roots, and the Mission cross-staffel-visibility escape clause
 * (`is_internal = false`).
 */
@ExtendWith(MockitoExtension.class)
class SquadronScopeServiceTest {

  @Mock private AuthHelperService authHelper;
  @Mock private UserRepository userRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private OperationRepository operationRepository;
  @Mock private HttpServletRequest request;
  @Mock private HttpSession session;

  @InjectMocks private SquadronScopeService service;

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
    void adminWithoutSession_returnsEmpty_admin_seesAllSquadrons() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(false)).thenReturn(null);

      assertTrue(service.currentSquadronId().isEmpty());
    }

    @Test
    void adminWithActiveSquadron_returnsThatSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(false)).thenReturn(session);
      when(session.getAttribute(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY))
          .thenReturn(SQUADRON_B_ID);

      assertEquals(Optional.of(SQUADRON_B_ID), service.currentSquadronId());
    }

    @Test
    void adminWithSessionButNoSquadronAttribute_returnsEmpty() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(false)).thenReturn(session);
      when(session.getAttribute(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY)).thenReturn(null);

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
      when(request.getSession(false)).thenReturn(null);

      assertTrue(service.currentSquadron().isEmpty());
    }
  }

  @Nested
  class CanSeeSquadronTests {

    @Test
    void adminWithoutSelection_canSeeAnySquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(false)).thenReturn(null);

      assertTrue(service.canSeeSquadron(SQUADRON_A_ID));
      assertTrue(service.canSeeSquadron(SQUADRON_B_ID));
    }

    @Test
    void adminWithSelection_seesOnlySelectedSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(false)).thenReturn(session);
      when(session.getAttribute(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY))
          .thenReturn(SQUADRON_A_ID);

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
      // canEdit is strict — the is_internal escape clause does NOT apply to writes.
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
      // Strict — no public-escape clause for inventory.
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
      when(request.getSession(false)).thenReturn(null);

      assertTrue(service.canSeeRefineryOrder(orderId));
      assertTrue(service.canEditRefineryOrder(orderId));
    }
  }

  @Nested
  class SetActiveSquadronTests {

    @Test
    void admin_canActivateSquadron() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(true)).thenReturn(session);

      service.setActiveSquadron(SQUADRON_A_ID);

      verify(session).setAttribute(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY, SQUADRON_A_ID);
      verify(session, never())
          .removeAttribute(eq(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY));
    }

    @Test
    void admin_canClearActiveSquadron_byPassingNull() {
      when(authHelper.isAdmin()).thenReturn(true);
      when(request.getSession(true)).thenReturn(session);

      service.setActiveSquadron(null);

      verify(session).removeAttribute(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY);
      verify(session, never())
          .setAttribute(eq(SquadronScopeService.ACTIVE_SQUADRON_SESSION_KEY), eq(SQUADRON_A_ID));
    }

    @Test
    void nonAdmin_rejectedWithIllegalState() {
      when(authHelper.isAdmin()).thenReturn(false);

      assertThrows(IllegalStateException.class, () -> service.setActiveSquadron(SQUADRON_A_ID));
    }
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
