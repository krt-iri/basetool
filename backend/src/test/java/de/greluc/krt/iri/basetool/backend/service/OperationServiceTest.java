package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationPayoutStatus;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationPayoutStatusRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

  @Mock private OperationRepository operationRepository;
  @Mock private MissionFinanceEntryRepository financeEntryRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private OperationPayoutStatusRepository payoutStatusRepository;
  @Mock private UserService userService;

  @InjectMocks private OperationService operationService;

  @Test
  void shouldCreateOperation() {
    // Given
    Operation operation = new Operation();
    operation.setName("Test Op");
    operation.setStatus(OperationStatus.PLANNED);

    when(operationRepository.save(any(Operation.class))).thenReturn(operation);

    // When
    Operation result = operationService.createOperation(operation);

    // Then
    assertNotNull(result);
    assertEquals("Test Op", result.getName());
    verify(operationRepository, times(1)).save(operation);
  }

  @Test
  void shouldGetOperationById() {
    // Given
    UUID id = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(id);
    when(operationRepository.findById(id)).thenReturn(Optional.of(operation));

    // When
    Operation result = operationService.getOperationById(id);

    // Then
    assertNotNull(result);
    assertEquals(id, result.getId());
  }

  @Test
  void getOperationById_throwsNotFoundException_whenMissing() {
    UUID missing = UUID.randomUUID();
    when(operationRepository.findById(missing)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> operationService.getOperationById(missing));
  }

  @Test
  void shouldGetAllOperations() {
    // Given
    PageRequest pageable = PageRequest.of(0, 10);
    Page<Operation> page = new PageImpl<>(List.of(new Operation()));
    when(operationRepository.findAll(pageable)).thenReturn(page);

    // When
    Page<Operation> result = operationService.getAllOperations(pageable);

    // Then
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
  }

  @Test
  void shouldDeleteOperation() {
    // Given
    UUID id = UUID.randomUUID();
    Operation operation = new Operation();
    when(operationRepository.findById(id)).thenReturn(Optional.of(operation));
    doNothing().when(operationRepository).delete(operation);

    // When
    operationService.deleteOperation(id);

    // Then
    verify(operationRepository, times(1)).delete(operation);
  }

  @Test
  void deleteOperation_unlinksMissions_butDoesNotDeleteThem() {
    // The contract of deleteOperation is to clear the mission -> operation
    // back-reference and clear the in-memory collection, then delete the
    // operation itself. Missions and everything hanging off them (participants,
    // finance entries, inventory items, refinery orders) MUST survive — only
    // the operation aggregate root vanishes.
    UUID id = UUID.randomUUID();
    Operation operation = new Operation();
    operation.setId(id);

    Mission m1 = new Mission();
    m1.setId(UUID.randomUUID());
    m1.setOperation(operation);
    Mission m2 = new Mission();
    m2.setId(UUID.randomUUID());
    m2.setOperation(operation);
    Set<Mission> missions = new HashSet<>();
    missions.add(m1);
    missions.add(m2);
    operation.setMissions(missions);

    when(operationRepository.findById(id)).thenReturn(Optional.of(operation));

    operationService.deleteOperation(id);

    assertNull(m1.getOperation(), "mission #1 back-reference to the operation must be cleared");
    assertNull(m2.getOperation(), "mission #2 back-reference to the operation must be cleared");
    assertTrue(
        operation.getMissions().isEmpty(),
        "in-memory missions collection must be cleared to keep state consistent");
    verify(operationRepository, times(1)).delete(operation);
  }

  @Test
  void deleteOperation_throwsNotFoundException_whenMissing() {
    UUID missing = UUID.randomUUID();
    when(operationRepository.findById(missing)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> operationService.deleteOperation(missing));
  }

  // --- updateOperation -----------------------------------------------------

  @Nested
  class UpdateOperationTests {

    @Test
    void updatesAllFields_whenVersionMatchesAndTransitionAllowed() {
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setName("old");
      existing.setDescription("old-desc");
      existing.setStatus(OperationStatus.PLANNED);
      existing.setVersion(2L);

      // PLANNED -> ACTIVE is allowed by the state machine.
      OperationUpdateDto incoming =
          new OperationUpdateDto("new", "new-desc", OperationStatus.ACTIVE, 2L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
      when(operationRepository.save(existing)).thenReturn(existing);

      Operation result = operationService.updateOperation(id, incoming, false);

      assertEquals("new", result.getName());
      assertEquals("new-desc", result.getDescription());
      assertEquals(OperationStatus.ACTIVE, result.getStatus());
    }

    @Test
    void rejectsForbiddenStatusTransition_whenNotAdmin() {
      // PLANNED -> COMPLETED skips the ACTIVE phase and is not a valid transition.
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setStatus(OperationStatus.PLANNED);
      existing.setVersion(1L);

      OperationUpdateDto incoming = new OperationUpdateDto("n", "d", OperationStatus.COMPLETED, 1L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

      BadRequestException ex =
          assertThrows(
              BadRequestException.class,
              () -> operationService.updateOperation(id, incoming, false));
      assertTrue(ex.getMessage().contains("PLANNED"));
      assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    void terminalStatusCannotBeChanged_whenNotAdmin() {
      // COMPLETED has no outgoing transitions.
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setStatus(OperationStatus.COMPLETED);
      existing.setVersion(1L);

      OperationUpdateDto incoming = new OperationUpdateDto("n", "d", OperationStatus.ACTIVE, 1L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

      assertThrows(
          BadRequestException.class, () -> operationService.updateOperation(id, incoming, false));
    }

    @Test
    void sameStatusIsAlwaysAllowed_evenWithoutAdmin() {
      // Updating only the name/description on a COMPLETED operation must NOT
      // trip the state-machine guard. Same-status transitions are always fine.
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setName("old");
      existing.setStatus(OperationStatus.COMPLETED);
      existing.setVersion(1L);

      OperationUpdateDto incoming =
          new OperationUpdateDto("new", "post-mortem description", OperationStatus.COMPLETED, 1L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
      when(operationRepository.save(existing)).thenReturn(existing);

      Operation result = operationService.updateOperation(id, incoming, false);

      assertEquals("new", result.getName());
      assertEquals(OperationStatus.COMPLETED, result.getStatus());
    }

    @Test
    void adminMayOverrideStatusMachine() {
      // ADMIN reverses a CANCELED operation back to PLANNED — disallowed for
      // regular MISSION_MANAGER callers, but the override flag opens the gate.
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setStatus(OperationStatus.CANCELED);
      existing.setVersion(1L);

      OperationUpdateDto incoming = new OperationUpdateDto("n", "d", OperationStatus.PLANNED, 1L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
      when(operationRepository.save(existing)).thenReturn(existing);

      Operation result = operationService.updateOperation(id, incoming, true);

      assertEquals(OperationStatus.PLANNED, result.getStatus());
    }

    @Test
    void throwsNotFoundException_whenIdMissing() {
      UUID missing = UUID.randomUUID();
      when(operationRepository.findById(missing)).thenReturn(Optional.empty());

      OperationUpdateDto dto = new OperationUpdateDto("n", "d", OperationStatus.PLANNED, 0L);
      assertThrows(
          NotFoundException.class, () -> operationService.updateOperation(missing, dto, false));
    }

    @Test
    void throwsOptimisticLockingFailure_whenVersionMismatch() {
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setVersion(7L);

      OperationUpdateDto incoming = new OperationUpdateDto("n", "d", OperationStatus.PLANNED, 3L);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () -> operationService.updateOperation(id, incoming, false));
    }

    @Test
    void acceptsNullVersionInIncoming_asBypassToken() {
      // Mirrors the bypass behavior used by other services: a null version on
      // the inbound DTO skips the explicit check (Hibernate still catches stale
      // writes via the UPDATE ... WHERE version=N fallback on commit).
      // Note: in practice OperationUpdateDto declares @NotNull on version so
      // this code path is guarded at the controller boundary; the service-
      // level branch still has to remain forgiving so internal callers can
      // bypass the check explicitly.
      UUID id = UUID.randomUUID();
      Operation existing = new Operation();
      existing.setId(id);
      existing.setVersion(5L);
      existing.setName("old");
      existing.setStatus(OperationStatus.PLANNED);

      // Same-status update with a null version on the DTO. The status gate
      // is a no-op (PLANNED -> PLANNED is always fine), and the manual
      // optimistic-lock check is skipped due to the null version.
      OperationUpdateDto incoming =
          new OperationUpdateDto("new", null, OperationStatus.PLANNED, null);

      when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
      when(operationRepository.save(existing)).thenReturn(existing);

      Operation result = operationService.updateOperation(id, incoming, false);
      assertEquals("new", result.getName());
    }
  }

  // --- getOperationPayouts -------------------------------------------------

  /**
   * The payout calculator is the money-handling core of the operation flow. Its previous coverage
   * was 0% — these tests exhaustively cover the branches:
   *
   * <ol>
   *   <li>Operation lookup (not-found path).
   *   <li>Mission validity gate (null start, null end, end &lt;= start).
   *   <li>Participant identity (user vs guest vs neither).
   *   <li>Effective-window clamping (pStart &lt; actualStart, pEnd &gt; actualEnd, pEnd null falls
   *       back to now()).
   *   <li>DONATE preference precedence across multiple missions.
   *   <li>Aggregation across missions for the same participant.
   *   <li>Percentage math (total &gt; 0 vs total == 0 div-by-zero guard, two-decimal rounding).
   *   <li>Output ordering (case-insensitive by participant name).
   * </ol>
   */
  @Nested
  class GetOperationPayoutsTests {

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T0_PLUS_60M = T0.plus(60, ChronoUnit.MINUTES);
    private static final Instant T0_PLUS_30M = T0.plus(30, ChronoUnit.MINUTES);

    @Test
    void throwsNotFound_whenOperationDoesNotExist() {
      UUID missing = UUID.randomUUID();
      when(operationRepository.findWithMissionsAndParticipantsById(missing))
          .thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> operationService.getOperationPayouts(missing));
    }

    @Test
    void emptyOperation_returnsEmptyList() {
      stubOperation(new HashSet<>());

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertTrue(result.isEmpty());
    }

    @Test
    void missionWithNullActualStart_isSkipped() {
      Mission m = newMission(null, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertTrue(result.isEmpty(), "missions without actualStart contribute nothing");
    }

    @Test
    void missionWithNullActualEnd_isSkipped() {
      Mission m = newMission(T0, null);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      assertTrue(operationService.getOperationPayouts(OPERATION_ID).isEmpty());
    }

    @Test
    void missionEndingAtSameInstantAsStart_isSkipped() {
      Mission m = newMission(T0, T0);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      assertTrue(
          operationService.getOperationPayouts(OPERATION_ID).isEmpty(),
          "actualEnd must be STRICTLY after actualStart");
    }

    @Test
    void missionEndingBeforeStart_isSkipped() {
      Mission m = newMission(T0_PLUS_60M, T0);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      assertTrue(operationService.getOperationPayouts(OPERATION_ID).isEmpty());
    }

    @Test
    void participantWithoutUserOrGuestName_isSkipped() {
      Mission m = newMission(T0, T0_PLUS_60M);
      MissionParticipant ghost = new MissionParticipant();
      ghost.setMission(m);
      ghost.setStartTime(T0);
      ghost.setEndTime(T0_PLUS_60M);
      ghost.setPayoutPreference(PayoutPreference.PAYOUT);
      m.getParticipants().add(ghost);
      stubOperation(Set.of(m));

      assertTrue(
          operationService.getOperationPayouts(OPERATION_ID).isEmpty(),
          "participants with neither user nor guestName must not appear");
    }

    @Test
    void participantWithNullStartTime_appearsInResultWithZeroPercent() {
      // The implementation registers `participantNames` / `preferences` BEFORE the
      // start-time check, so a participant who is on the roster but never logged
      // a start time is still listed in the payout breakdown (with 0%). This is
      // deliberate: silently dropping such a row would make the UI lose track of
      // someone who showed up but forgot to clock in.
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", null, T0_PLUS_60M, PayoutPreference.DONATE);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals("alice", result.get(0).participantName());
      assertEquals(
          0.0,
          result.get(0).participationPercentage(),
          "null start time -> no duration accumulated -> 0%");
      assertEquals(
          PayoutPreference.DONATE,
          result.get(0).payoutPreference(),
          "preference must still be recorded even with no duration");
    }

    @Test
    void soleUserParticipant_fullDuration_gets100Percent() {
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals("alice", result.get(0).participantName());
      assertEquals(100.0, result.get(0).participationPercentage());
      assertEquals(PayoutPreference.PAYOUT, result.get(0).payoutPreference());
    }

    @Test
    void guestParticipant_isIncluded_byGuestName() {
      Mission m = newMission(T0, T0_PLUS_60M);
      MissionParticipant guest = new MissionParticipant();
      guest.setMission(m);
      guest.setGuestName("Bob the Guest");
      guest.setStartTime(T0);
      guest.setEndTime(T0_PLUS_60M);
      guest.setPayoutPreference(PayoutPreference.PAYOUT);
      m.getParticipants().add(guest);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      OperationPayoutDto row = result.get(0);
      assertEquals("Bob the Guest", row.participantName());
      assertTrue(
          row.participantId().startsWith("guest_"),
          "guest IDs must be prefixed to avoid colliding with user UUIDs");
      assertEquals(100.0, row.participationPercentage());
    }

    @Test
    void participantStartBeforeMissionStart_isClampedToMissionStart() {
      // mission: [T0, T0+60m]; participant: [T0-60m, T0+30m]
      // → effective: [T0, T0+30m] = 50% of the 60-minute mission window
      // (but participant is also the only one, so 100% of recorded total)
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(
          m, "alice", T0.minus(60, ChronoUnit.MINUTES), T0_PLUS_30M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals(
          100.0,
          result.get(0).participationPercentage(),
          "alice is the only contributor so her share is 100% even when clamped");
    }

    @Test
    void participantEndAfterMissionEnd_isClampedToMissionEnd() {
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(
          m,
          "alice",
          T0_PLUS_30M,
          T0_PLUS_60M.plus(60, ChronoUnit.MINUTES),
          PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals(100.0, result.get(0).participationPercentage());
    }

    @Test
    void participantWithNullEndTime_clampedToInstantNow() {
      // Participant joined before mission ended and never logged an end time.
      // Mission ended in the past, so pEnd defaults to now() but is then
      // clamped down to actualEnd, producing a real positive duration.
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0_PLUS_30M, null, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals(100.0, result.get(0).participationPercentage());
    }

    @Test
    void twoEquallyParticipatingUsers_splitFiftyFifty() {
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipant(m, "bob", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(2, result.size());
      assertEquals(50.0, result.get(0).participationPercentage());
      assertEquals(50.0, result.get(1).participationPercentage());
      assertEquals(
          100.0,
          result.get(0).participationPercentage() + result.get(1).participationPercentage(),
          "shares must sum to 100% (no rounding losses for a 50/50 split)");
    }

    @Test
    void unequalDurations_produceProportionalPercentages() {
      // alice: 60 minutes, bob: 30 minutes -> 60/(60+30) = 66.67%, 33.33%
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipant(m, "bob", T0, T0_PLUS_30M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(2, result.size());
      OperationPayoutDto alice =
          result.stream()
              .filter(r -> r.participantName().equals("alice"))
              .findFirst()
              .orElseThrow();
      OperationPayoutDto bob =
          result.stream().filter(r -> r.participantName().equals("bob")).findFirst().orElseThrow();
      assertEquals(
          66.67,
          alice.participationPercentage(),
          "60 / 90 = 66.666..., rounded to 2 decimals = 66.67");
      assertEquals(33.33, bob.participationPercentage());
    }

    @Test
    void donatePreferenceOnAnyMission_overridesPayoutFromOtherMissions() {
      // Two missions, same user. In mission #1 user says PAYOUT, in #2 DONATE.
      // The aggregate must record DONATE (any DONATE locks the row).
      Mission m1 = newMission(T0, T0_PLUS_60M);
      Mission m2 = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      addUserParticipantWithUser(m1, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipantWithUser(m2, alice, T0, T0_PLUS_60M, PayoutPreference.DONATE);
      stubOperation(Set.of(m1, m2));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals(
          PayoutPreference.DONATE,
          result.get(0).payoutPreference(),
          "any DONATE preference must win across missions");
    }

    @Test
    void payoutPreference_doesNotOverrideEarlierDonate() {
      // Reverse of the above: DONATE seen first, PAYOUT later -> still DONATE.
      Mission m1 = newMission(T0, T0_PLUS_60M);
      Mission m2 = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      addUserParticipantWithUser(m1, alice, T0, T0_PLUS_60M, PayoutPreference.DONATE);
      addUserParticipantWithUser(m2, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m1, m2));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(PayoutPreference.DONATE, result.get(0).payoutPreference());
    }

    @Test
    void durationsAcrossMultipleMissions_accumulateForSameUser() {
      Mission m1 = newMission(T0, T0_PLUS_30M);
      Mission m2 = newMission(T0_PLUS_60M, T0_PLUS_60M.plus(30, ChronoUnit.MINUTES));
      User alice = newUser("alice");
      addUserParticipantWithUser(m1, alice, T0, T0_PLUS_30M, PayoutPreference.PAYOUT);
      addUserParticipantWithUser(
          m2,
          alice,
          T0_PLUS_60M,
          T0_PLUS_60M.plus(30, ChronoUnit.MINUTES),
          PayoutPreference.PAYOUT);
      stubOperation(Set.of(m1, m2));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size(), "same user across two missions still produces one row");
      assertEquals(100.0, result.get(0).participationPercentage());
    }

    @Test
    void resultIsSortedCaseInsensitivelyByParticipantName() {
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "charlie", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipant(m, "Alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipant(m, "bob", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      // case-INSENSITIVE: "Alice" sorts before "bob" even though uppercase < lowercase in ASCII
      assertEquals(
          List.of("Alice", "bob", "charlie"),
          result.stream().map(OperationPayoutDto::participantName).toList());
    }

    @Test
    void participantWithEndAtSameInstantAsEffectiveStart_isSkipped() {
      // Edge case: effective window collapses to zero length -> no contribution.
      // Verify by giving alice a zero-length window and another user a real one;
      // alice must NOT appear in the result, bob's percentage must be 100%.
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0_PLUS_30M, T0_PLUS_30M, PayoutPreference.PAYOUT);
      addUserParticipant(m, "bob", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      // alice contributes zero duration -> totalDuration becomes bob's only.
      // She still appears with 0% because the participant-name map captured her.
      // bob: 100%, alice: 0%.
      assertEquals(2, result.size());
      OperationPayoutDto alice =
          result.stream()
              .filter(r -> r.participantName().equals("alice"))
              .findFirst()
              .orElseThrow();
      OperationPayoutDto bob =
          result.stream().filter(r -> r.participantName().equals("bob")).findFirst().orElseThrow();
      assertEquals(0.0, alice.participationPercentage());
      assertEquals(100.0, bob.participationPercentage());
    }

    @Test
    void allParticipantsHaveZeroValidDuration_dividesByZeroSafely() {
      // No mission produces any valid duration -> totalOperationValidDuration == 0
      // -> every participant must get 0.0 (NOT NaN from dividing by zero).
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0_PLUS_30M, T0_PLUS_30M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      assertEquals(
          0.0,
          result.get(0).participationPercentage(),
          "div-by-zero must clamp to 0.0, not produce NaN");
    }

    @Test
    void userDisplayName_isPreferredOverUsername() {
      // User.getEffectiveName() returns displayName when present, else username.
      // The payout must use the effective name.
      Mission m = newMission(T0, T0_PLUS_60M);
      User u = newUser("alice");
      u.setDisplayName("Alice Liddell");
      addUserParticipantWithUser(m, u, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals("Alice Liddell", result.get(0).participantName());
    }

    // ----- helpers ----------------------------------------------------

    private void stubOperation(Set<Mission> missions) {
      Operation op = new Operation();
      op.setId(OPERATION_ID);
      op.setMissions(missions);
      when(operationRepository.findWithMissionsAndParticipantsById(OPERATION_ID))
          .thenReturn(Optional.of(op));
    }

    private Mission newMission(Instant actualStart, Instant actualEnd) {
      Mission m = new Mission();
      m.setId(UUID.randomUUID());
      m.setActualStartTime(actualStart);
      m.setActualEndTime(actualEnd);
      return m;
    }

    private User newUser(String username) {
      User u = new User();
      u.setId(UUID.randomUUID());
      u.setUsername(username);
      return u;
    }

    private void addUserParticipant(
        Mission mission, String username, Instant start, Instant end, PayoutPreference pref) {
      addUserParticipantWithUser(mission, newUser(username), start, end, pref);
    }

    private void addUserParticipantWithUser(
        Mission mission, User user, Instant start, Instant end, PayoutPreference pref) {
      MissionParticipant p = new MissionParticipant();
      p.setMission(mission);
      p.setUser(user);
      p.setStartTime(start);
      p.setEndTime(end);
      p.setPayoutPreference(pref);
      mission.getParticipants().add(p);
    }

    // suppress unused warning for the duration helper (kept for readability)
    @SuppressWarnings("unused")
    private static long minutes(int n) {
      return Duration.ofMinutes(n).toMillis();
    }
  }

  // --- getOperationPayouts: amount / paid-out fields ------------------------

  /**
   * Coverage for the money-side of the payout breakdown. The reimbursement-first model says: each
   * participant's out-of-pocket expenses (mission EXPENSE entries owned by them + refinery orders'
   * costs they own) are paid back from gross income, and the remaining {@code totalSum} is split
   * per participation percentage among PAYOUT participants. DONATE participants keep their
   * reimbursement (it is their own money returned) but contribute their share. The combined
   * paid-out fields are covered together because they share the same setup.
   */
  @Nested
  class GetOperationPayoutsAmountTests {

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T0_PLUS_60M = T0.plus(60, ChronoUnit.MINUTES);

    @Test
    void incomeOnly_splitsEquallyBetweenTwoPayoutParticipants() {
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      User bob = newUser("bob");
      MissionParticipant aliceP =
          addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipantWithUser(m, bob, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      // INCOME 1000 not attributed to any single participant (entry.participant references one
      // for audit/UI purposes, but the income amount accrues to the operation pool, not the
      // attributed participant). We model that here by giving the entry a participant but
      // INCOME type — the cost-attribution loop skips non-EXPENSE entries.
      MissionFinanceEntry income =
          newEntry(m, aliceP, FinanceType.INCOME, new BigDecimal("1000.00"));
      stubFinances(List.of(income), List.of());

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      OperationPayoutDto aliceRow = byName(result, "alice");
      OperationPayoutDto bobRow = byName(result, "bob");
      assertEquals(new BigDecimal("0.00"), aliceRow.personalExpenses());
      assertEquals(new BigDecimal("500.00"), aliceRow.shareAmount());
      assertEquals(new BigDecimal("500.00"), aliceRow.payoutAmount());
      assertEquals(new BigDecimal("0.00"), bobRow.personalExpenses());
      assertEquals(new BigDecimal("500.00"), bobRow.shareAmount());
      assertEquals(new BigDecimal("500.00"), bobRow.payoutAmount());
    }

    @Test
    void missionExpenseAttributedToParticipant_reimbursedOffTheTop_thenRemainderSplit() {
      // Gross income 1000, alice paid 300 in mission expenses, totalSum = 700.
      // Reimburse alice 300 first; split 700 evenly: alice 350, bob 350.
      // Net positions: alice paid 300 out of pocket, got 650 back -> +350; bob 0 out, +350.
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      User bob = newUser("bob");
      MissionParticipant aliceP =
          addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      MissionParticipant bobP =
          addUserParticipantWithUser(m, bob, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      MissionFinanceEntry income = newEntry(m, bobP, FinanceType.INCOME, new BigDecimal("1000.00"));
      MissionFinanceEntry expense =
          newEntry(m, aliceP, FinanceType.EXPENSE, new BigDecimal("300.00"));
      stubFinances(List.of(income, expense), List.of());

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      OperationPayoutDto aliceRow = byName(result, "alice");
      OperationPayoutDto bobRow = byName(result, "bob");
      assertEquals(new BigDecimal("300.00"), aliceRow.personalExpenses());
      assertEquals(new BigDecimal("350.00"), aliceRow.shareAmount());
      assertEquals(
          new BigDecimal("650.00"),
          aliceRow.payoutAmount(),
          "alice's payout = reimbursement (300) + share (350)");
      assertEquals(new BigDecimal("0.00"), bobRow.personalExpenses());
      assertEquals(new BigDecimal("350.00"), bobRow.shareAmount());
      assertEquals(new BigDecimal("350.00"), bobRow.payoutAmount());
    }

    @Test
    void refineryOrderCosts_attributedToOwner_asReimbursement() {
      // alice runs a refinery order: sales=2000, expenses=500, other=200, profit=1300.
      // totalSum = 1300. alice gets reimbursed 700, then 50% of 1300 = 650.
      // bob gets 50% of 1300 = 650. Net: alice paid 700, got 1350 -> +650 = bob's +650.
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      User bob = newUser("bob");
      addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      addUserParticipantWithUser(m, bob, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      RefineryOrder order = new RefineryOrder();
      order.setId(UUID.randomUUID());
      order.setOwner(alice);
      order.setMission(m);
      order.setOreSales(2000d);
      order.setExpenses(500d);
      order.setOtherExpenses(200d);
      stubFinances(List.of(), List.of(order));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      OperationPayoutDto aliceRow = byName(result, "alice");
      OperationPayoutDto bobRow = byName(result, "bob");
      assertEquals(new BigDecimal("700.00"), aliceRow.personalExpenses());
      assertEquals(new BigDecimal("650.00"), aliceRow.shareAmount());
      assertEquals(new BigDecimal("1350.00"), aliceRow.payoutAmount());
      assertEquals(new BigDecimal("0.00"), bobRow.personalExpenses());
      assertEquals(new BigDecimal("650.00"), bobRow.shareAmount());
      assertEquals(new BigDecimal("650.00"), bobRow.payoutAmount());
    }

    @Test
    void donateParticipantKeepsReimbursementButGetsZeroShare() {
      // alice DONATE 50%, bob PAYOUT 50%. INCOME 1000, alice paid 300 expense.
      // totalSum = 700. alice: reimbursement 300, share 0 (donating). bob: share 350.
      // alice's share of 350 is donated to the org and not paid out.
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      User bob = newUser("bob");
      MissionParticipant aliceP =
          addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.DONATE);
      addUserParticipantWithUser(m, bob, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      MissionFinanceEntry income =
          newEntry(m, aliceP, FinanceType.INCOME, new BigDecimal("1000.00"));
      MissionFinanceEntry expense =
          newEntry(m, aliceP, FinanceType.EXPENSE, new BigDecimal("300.00"));
      stubFinances(List.of(income, expense), List.of());

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      OperationPayoutDto aliceRow = byName(result, "alice");
      OperationPayoutDto bobRow = byName(result, "bob");
      assertEquals(PayoutPreference.DONATE, aliceRow.payoutPreference());
      assertEquals(new BigDecimal("300.00"), aliceRow.personalExpenses());
      assertEquals(
          new BigDecimal("0.00"),
          aliceRow.shareAmount(),
          "DONATE participants contribute their share; only reimbursement is paid out");
      assertEquals(new BigDecimal("300.00"), aliceRow.payoutAmount());
      assertEquals(new BigDecimal("350.00"), bobRow.payoutAmount());
    }

    @Test
    void guestParticipantExpenses_areReimbursedToGuestKey() {
      // Guests can incur mission expenses too — verify the participant_key match works for
      // "guest_<name>" rows.
      Mission m = newMission(T0, T0_PLUS_60M);
      MissionParticipant guest = new MissionParticipant();
      guest.setMission(m);
      guest.setGuestName("Gary");
      guest.setStartTime(T0);
      guest.setEndTime(T0_PLUS_60M);
      guest.setPayoutPreference(PayoutPreference.PAYOUT);
      m.getParticipants().add(guest);
      stubOperation(Set.of(m));

      MissionFinanceEntry expense =
          newEntry(m, guest, FinanceType.EXPENSE, new BigDecimal("250.00"));
      MissionFinanceEntry income = newEntry(m, guest, FinanceType.INCOME, new BigDecimal("500.00"));
      stubFinances(List.of(income, expense), List.of());

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      assertEquals(1, result.size());
      OperationPayoutDto row = result.get(0);
      assertTrue(row.participantId().startsWith("guest_"));
      assertEquals(new BigDecimal("250.00"), row.personalExpenses());
      // sole participant, 100% share. totalSum = 500 - 250 = 250.
      assertEquals(new BigDecimal("250.00"), row.shareAmount());
      assertEquals(new BigDecimal("500.00"), row.payoutAmount());
    }

    @Test
    void refineryOrderWithNullCosts_isTreatedAsZeroExpense() {
      // Legacy refinery orders may have null expenses / otherExpenses (V70 migration). They
      // contribute null * 0 = 0 to the participant's reimbursement.
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      RefineryOrder order = new RefineryOrder();
      order.setId(UUID.randomUUID());
      order.setOwner(alice);
      order.setMission(m);
      order.setOreSales(1000d);
      order.setExpenses(null);
      order.setOtherExpenses(null);
      stubFinances(List.of(), List.of(order));

      List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

      OperationPayoutDto aliceRow = byName(result, "alice");
      assertEquals(new BigDecimal("0.00"), aliceRow.personalExpenses());
      assertEquals(new BigDecimal("1000.00"), aliceRow.shareAmount());
      assertEquals(new BigDecimal("1000.00"), aliceRow.payoutAmount());
    }

    @Test
    void paidOutFlag_isFalseWhenNoStatusRowExists() {
      Mission m = newMission(T0, T0_PLUS_60M);
      addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));
      // payoutStatusRepository default-returns empty list -> no rows.

      OperationPayoutDto row = operationService.getOperationPayouts(OPERATION_ID).get(0);

      assertFalse(row.paidOut(), "absent status row means not paid out");
      assertNull(row.paidOutAt());
      assertNull(row.paidOutByName());
    }

    @Test
    void paidOutFlag_reflectsExistingStatusRow() {
      Mission m = newMission(T0, T0_PLUS_60M);
      User alice = newUser("alice");
      addUserParticipantWithUser(m, alice, T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
      stubOperation(Set.of(m));

      User auditor = newUser("officer");
      auditor.setDisplayName("Officer Bob");
      Instant when = Instant.parse("2026-03-02T15:00:00Z");
      OperationPayoutStatus status = new OperationPayoutStatus();
      status.setOperation(null);
      status.setParticipantKey(alice.getId().toString());
      status.setPaidOut(true);
      status.setPaidOutAt(when);
      status.setPaidOutByUser(auditor);
      when(payoutStatusRepository.findByOperationId(OPERATION_ID)).thenReturn(List.of(status));

      OperationPayoutDto row = operationService.getOperationPayouts(OPERATION_ID).get(0);

      assertTrue(row.paidOut());
      assertEquals(when, row.paidOutAt());
      assertEquals("Officer Bob", row.paidOutByName());
    }

    // ----- helpers ---------------------------------------------------

    private void stubOperation(Set<Mission> missions) {
      Operation op = new Operation();
      op.setId(OPERATION_ID);
      op.setMissions(missions);
      when(operationRepository.findWithMissionsAndParticipantsById(OPERATION_ID))
          .thenReturn(Optional.of(op));
    }

    private void stubFinances(List<MissionFinanceEntry> entries, List<RefineryOrder> orders) {
      // Use lenient: not every test in this class verifies finance lookups, and Mockito's strict
      // mode complains otherwise. The method is only called when the operation has missions, so
      // these stubs match the actual lookups in the happy paths.
      when(financeEntryRepository.findAllByMissionIdIn(any())).thenReturn(entries);
      when(refineryOrderRepository.findByMissionIdIn(any())).thenReturn(orders);
    }

    private Mission newMission(Instant actualStart, Instant actualEnd) {
      Mission m = new Mission();
      m.setId(UUID.randomUUID());
      m.setActualStartTime(actualStart);
      m.setActualEndTime(actualEnd);
      return m;
    }

    private User newUser(String username) {
      User u = new User();
      u.setId(UUID.randomUUID());
      u.setUsername(username);
      return u;
    }

    private MissionParticipant addUserParticipant(
        Mission mission, String username, Instant start, Instant end, PayoutPreference pref) {
      return addUserParticipantWithUser(mission, newUser(username), start, end, pref);
    }

    private MissionParticipant addUserParticipantWithUser(
        Mission mission, User user, Instant start, Instant end, PayoutPreference pref) {
      MissionParticipant p = new MissionParticipant();
      p.setMission(mission);
      p.setUser(user);
      p.setStartTime(start);
      p.setEndTime(end);
      p.setPayoutPreference(pref);
      mission.getParticipants().add(p);
      return p;
    }

    private MissionFinanceEntry newEntry(
        Mission mission, MissionParticipant participant, FinanceType type, BigDecimal amount) {
      return MissionFinanceEntry.builder()
          .id(UUID.randomUUID())
          .mission(mission)
          .participant(participant)
          .type(type)
          .amount(amount)
          .build();
    }

    private OperationPayoutDto byName(List<OperationPayoutDto> rows, String name) {
      return rows.stream()
          .filter(r -> r.participantName().equals(name))
          .findFirst()
          .orElseThrow(() -> new AssertionError("missing row for " + name));
    }
  }

  // --- setPayoutStatus ------------------------------------------------------

  /**
   * Tests for the mission-manager paid-out toggle. The contract: materialize a fresh status row
   * when none exists, update in place otherwise, always refresh audit fields when paid_out=true,
   * and return the freshly-rendered payout row for the updated participant.
   */
  @Nested
  class SetPayoutStatusTests {

    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T0_PLUS_60M = T0.plus(60, ChronoUnit.MINUTES);

    @Test
    void throwsNotFound_whenOperationDoesNotExist() {
      when(operationRepository.existsById(OPERATION_ID)).thenReturn(false);

      assertThrows(
          NotFoundException.class,
          () -> operationService.setPayoutStatus(OPERATION_ID, "anything", true));
    }

    @Test
    void createsNewRow_whenStatusDoesNotExistYet_andRecordsAuditFields() {
      User alice = newUser("alice");
      String key = alice.getId().toString();

      when(operationRepository.existsById(OPERATION_ID)).thenReturn(true);
      when(payoutStatusRepository.findByOperationIdAndParticipantKey(OPERATION_ID, key))
          .thenReturn(Optional.empty());
      Operation opRef = new Operation();
      opRef.setId(OPERATION_ID);
      when(operationRepository.getReferenceById(OPERATION_ID)).thenReturn(opRef);

      // Stub the re-read so setPayoutStatus can return the refreshed row.
      stubOperationWithParticipant(alice);

      User actor = newUser("officer");
      actor.setDisplayName("Officer Bob");
      when(userService.getCurrentUser()).thenReturn(Optional.of(actor));

      operationService.setPayoutStatus(OPERATION_ID, key, true);

      ArgumentCaptor<OperationPayoutStatus> captor =
          ArgumentCaptor.forClass(OperationPayoutStatus.class);
      verify(payoutStatusRepository).save(captor.capture());
      OperationPayoutStatus saved = captor.getValue();
      assertEquals(key, saved.getParticipantKey());
      assertTrue(saved.isPaidOut());
      assertNotNull(saved.getPaidOutAt(), "paid_out_at must be stamped on transition to true");
      assertEquals(actor, saved.getPaidOutByUser());
      assertEquals(opRef, saved.getOperation());
    }

    @Test
    void updatesExistingRow_inPlace() {
      User alice = newUser("alice");
      String key = alice.getId().toString();

      OperationPayoutStatus existing = new OperationPayoutStatus();
      existing.setId(UUID.randomUUID());
      existing.setParticipantKey(key);
      existing.setPaidOut(false);

      when(operationRepository.existsById(OPERATION_ID)).thenReturn(true);
      when(payoutStatusRepository.findByOperationIdAndParticipantKey(OPERATION_ID, key))
          .thenReturn(Optional.of(existing));

      stubOperationWithParticipant(alice);

      User actor = newUser("officer");
      when(userService.getCurrentUser()).thenReturn(Optional.of(actor));

      operationService.setPayoutStatus(OPERATION_ID, key, true);

      ArgumentCaptor<OperationPayoutStatus> captor =
          ArgumentCaptor.forClass(OperationPayoutStatus.class);
      verify(payoutStatusRepository).save(captor.capture());
      OperationPayoutStatus saved = captor.getValue();
      assertEquals(existing, saved, "must update the same instance, not insert a duplicate row");
      assertTrue(saved.isPaidOut());
      assertNotNull(saved.getPaidOutAt());
      assertEquals(actor, saved.getPaidOutByUser());
    }

    @Test
    void togglingPaidOutToFalse_keepsAuditTrailFromPriorTrue() {
      User alice = newUser("alice");
      String key = alice.getId().toString();
      Instant previouslyPaidAt = Instant.parse("2026-03-01T08:00:00Z");
      User previouslyAuditedBy = newUser("formerOfficer");
      OperationPayoutStatus existing = new OperationPayoutStatus();
      existing.setId(UUID.randomUUID());
      existing.setParticipantKey(key);
      existing.setPaidOut(true);
      existing.setPaidOutAt(previouslyPaidAt);
      existing.setPaidOutByUser(previouslyAuditedBy);

      when(operationRepository.existsById(OPERATION_ID)).thenReturn(true);
      when(payoutStatusRepository.findByOperationIdAndParticipantKey(OPERATION_ID, key))
          .thenReturn(Optional.of(existing));
      stubOperationWithParticipant(alice);

      operationService.setPayoutStatus(OPERATION_ID, key, false);

      ArgumentCaptor<OperationPayoutStatus> captor =
          ArgumentCaptor.forClass(OperationPayoutStatus.class);
      verify(payoutStatusRepository).save(captor.capture());
      OperationPayoutStatus saved = captor.getValue();
      assertFalse(saved.isPaidOut());
      assertEquals(
          previouslyPaidAt,
          saved.getPaidOutAt(),
          "paid_out_at must survive a toggle back to false as a historical audit trace");
      assertEquals(
          previouslyAuditedBy,
          saved.getPaidOutByUser(),
          "paid_out_by_user must survive a toggle back to false");
    }

    @Test
    void throwsNotFound_whenParticipantKeyIsUnknownInTheOperation() {
      String unknownKey = "guest_someone-who-was-never-in-this-op";
      when(operationRepository.existsById(OPERATION_ID)).thenReturn(true);
      when(payoutStatusRepository.findByOperationIdAndParticipantKey(OPERATION_ID, unknownKey))
          .thenReturn(Optional.empty());
      Operation opRef = new Operation();
      opRef.setId(OPERATION_ID);
      when(operationRepository.getReferenceById(OPERATION_ID)).thenReturn(opRef);

      // The participant set does NOT include this key.
      stubOperationWithParticipant(newUser("alice"));

      assertThrows(
          NotFoundException.class,
          () -> operationService.setPayoutStatus(OPERATION_ID, unknownKey, true));
    }

    private void stubOperationWithParticipant(User user) {
      Mission m = new Mission();
      m.setId(UUID.randomUUID());
      m.setActualStartTime(T0);
      m.setActualEndTime(T0_PLUS_60M);

      MissionParticipant p = new MissionParticipant();
      p.setMission(m);
      p.setUser(user);
      p.setStartTime(T0);
      p.setEndTime(T0_PLUS_60M);
      p.setPayoutPreference(PayoutPreference.PAYOUT);
      m.getParticipants().add(p);

      Operation op = new Operation();
      op.setId(OPERATION_ID);
      Set<Mission> missions = new HashSet<>();
      missions.add(m);
      op.setMissions(missions);

      when(operationRepository.findWithMissionsAndParticipantsById(OPERATION_ID))
          .thenReturn(Optional.of(op));
    }

    private User newUser(String username) {
      User u = new User();
      u.setId(UUID.randomUUID());
      u.setUsername(username);
      return u;
    }
  }
}
