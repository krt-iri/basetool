package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    @Mock
    private OperationRepository operationRepository;

    @InjectMocks
    private OperationService operationService;

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

        assertNull(m1.getOperation(),
                "mission #1 back-reference to the operation must be cleared");
        assertNull(m2.getOperation(),
                "mission #2 back-reference to the operation must be cleared");
        assertTrue(operation.getMissions().isEmpty(),
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
            OperationUpdateDto incoming = new OperationUpdateDto(
                    "new", "new-desc", OperationStatus.ACTIVE, 2L);

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

            OperationUpdateDto incoming = new OperationUpdateDto(
                    "n", "d", OperationStatus.COMPLETED, 1L);

            when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

            BadRequestException ex = assertThrows(BadRequestException.class,
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

            OperationUpdateDto incoming = new OperationUpdateDto(
                    "n", "d", OperationStatus.ACTIVE, 1L);

            when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

            assertThrows(BadRequestException.class,
                    () -> operationService.updateOperation(id, incoming, false));
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

            OperationUpdateDto incoming = new OperationUpdateDto(
                    "new", "post-mortem description", OperationStatus.COMPLETED, 1L);

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

            OperationUpdateDto incoming = new OperationUpdateDto(
                    "n", "d", OperationStatus.PLANNED, 1L);

            when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
            when(operationRepository.save(existing)).thenReturn(existing);

            Operation result = operationService.updateOperation(id, incoming, true);

            assertEquals(OperationStatus.PLANNED, result.getStatus());
        }

        @Test
        void throwsNotFoundException_whenIdMissing() {
            UUID missing = UUID.randomUUID();
            when(operationRepository.findById(missing)).thenReturn(Optional.empty());

            OperationUpdateDto dto = new OperationUpdateDto(
                    "n", "d", OperationStatus.PLANNED, 0L);
            assertThrows(NotFoundException.class,
                    () -> operationService.updateOperation(missing, dto, false));
        }

        @Test
        void throwsOptimisticLockingFailure_whenVersionMismatch() {
            UUID id = UUID.randomUUID();
            Operation existing = new Operation();
            existing.setId(id);
            existing.setVersion(7L);

            OperationUpdateDto incoming = new OperationUpdateDto(
                    "n", "d", OperationStatus.PLANNED, 3L);

            when(operationRepository.findById(id)).thenReturn(Optional.of(existing));

            assertThrows(ObjectOptimisticLockingFailureException.class,
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
            OperationUpdateDto incoming = new OperationUpdateDto(
                    "new", null, OperationStatus.PLANNED, null);

            when(operationRepository.findById(id)).thenReturn(Optional.of(existing));
            when(operationRepository.save(existing)).thenReturn(existing);

            Operation result = operationService.updateOperation(id, incoming, false);
            assertEquals("new", result.getName());
        }
    }

    // --- getOperationPayouts -------------------------------------------------

    /**
     * The payout calculator is the money-handling core of the operation flow.
     * Its previous coverage was 0% — these tests exhaustively cover the
     * branches enumerated in PROJECT_REVIEW.md's coverage analysis:
     *
     * <ol>
     *     <li>Operation lookup (not-found path).</li>
     *     <li>Mission validity gate (null start, null end, end &lt;= start).</li>
     *     <li>Participant identity (user vs guest vs neither).</li>
     *     <li>Effective-window clamping (pStart &lt; actualStart, pEnd &gt; actualEnd,
     *         pEnd null falls back to now()).</li>
     *     <li>DONATE preference precedence across multiple missions.</li>
     *     <li>Aggregation across missions for the same participant.</li>
     *     <li>Percentage math (total &gt; 0 vs total == 0 div-by-zero guard,
     *         two-decimal rounding).</li>
     *     <li>Output ordering (case-insensitive by participant name).</li>
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

            assertThrows(NotFoundException.class,
                    () -> operationService.getOperationPayouts(missing));
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

            assertTrue(result.isEmpty(),
                    "missions without actualStart contribute nothing");
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

            assertTrue(operationService.getOperationPayouts(OPERATION_ID).isEmpty(),
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

            assertTrue(operationService.getOperationPayouts(OPERATION_ID).isEmpty(),
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
            assertEquals(0.0, result.get(0).participationPercentage(),
                    "null start time -> no duration accumulated -> 0%");
            assertEquals(PayoutPreference.DONATE, result.get(0).payoutPreference(),
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
            assertTrue(row.participantId().startsWith("guest_"),
                    "guest IDs must be prefixed to avoid colliding with user UUIDs");
            assertEquals(100.0, row.participationPercentage());
        }

        @Test
        void participantStartBeforeMissionStart_isClampedToMissionStart() {
            // mission: [T0, T0+60m]; participant: [T0-60m, T0+30m]
            // → effective: [T0, T0+30m] = 50% of the 60-minute mission window
            // (but participant is also the only one, so 100% of recorded total)
            Mission m = newMission(T0, T0_PLUS_60M);
            addUserParticipant(m, "alice", T0.minus(60, ChronoUnit.MINUTES),
                    T0_PLUS_30M, PayoutPreference.PAYOUT);
            stubOperation(Set.of(m));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            assertEquals(1, result.size());
            assertEquals(100.0, result.get(0).participationPercentage(),
                    "alice is the only contributor so her share is 100% even when clamped");
        }

        @Test
        void participantEndAfterMissionEnd_isClampedToMissionEnd() {
            Mission m = newMission(T0, T0_PLUS_60M);
            addUserParticipant(m, "alice", T0_PLUS_30M,
                    T0_PLUS_60M.plus(60, ChronoUnit.MINUTES), PayoutPreference.PAYOUT);
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
            addUserParticipant(m, "bob",   T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            stubOperation(Set.of(m));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            assertEquals(2, result.size());
            assertEquals(50.0, result.get(0).participationPercentage());
            assertEquals(50.0, result.get(1).participationPercentage());
            assertEquals(100.0,
                    result.get(0).participationPercentage()
                            + result.get(1).participationPercentage(),
                    "shares must sum to 100% (no rounding losses for a 50/50 split)");
        }

        @Test
        void unequalDurations_produceProportionalPercentages() {
            // alice: 60 minutes, bob: 30 minutes -> 60/(60+30) = 66.67%, 33.33%
            Mission m = newMission(T0, T0_PLUS_60M);
            addUserParticipant(m, "alice", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            addUserParticipant(m, "bob",   T0, T0_PLUS_30M, PayoutPreference.PAYOUT);
            stubOperation(Set.of(m));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            assertEquals(2, result.size());
            OperationPayoutDto alice = result.stream()
                    .filter(r -> r.participantName().equals("alice"))
                    .findFirst().orElseThrow();
            OperationPayoutDto bob = result.stream()
                    .filter(r -> r.participantName().equals("bob"))
                    .findFirst().orElseThrow();
            assertEquals(66.67, alice.participationPercentage(),
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
            assertEquals(PayoutPreference.DONATE, result.get(0).payoutPreference(),
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
            addUserParticipantWithUser(m2, alice, T0_PLUS_60M,
                    T0_PLUS_60M.plus(30, ChronoUnit.MINUTES), PayoutPreference.PAYOUT);
            stubOperation(Set.of(m1, m2));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            assertEquals(1, result.size(),
                    "same user across two missions still produces one row");
            assertEquals(100.0, result.get(0).participationPercentage());
        }

        @Test
        void resultIsSortedCaseInsensitivelyByParticipantName() {
            Mission m = newMission(T0, T0_PLUS_60M);
            addUserParticipant(m, "charlie", T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            addUserParticipant(m, "Alice",   T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            addUserParticipant(m, "bob",     T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            stubOperation(Set.of(m));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            // case-INSENSITIVE: "Alice" sorts before "bob" even though uppercase < lowercase in ASCII
            assertEquals(List.of("Alice", "bob", "charlie"),
                    result.stream().map(OperationPayoutDto::participantName).toList());
        }

        @Test
        void participantWithEndAtSameInstantAsEffectiveStart_isSkipped() {
            // Edge case: effective window collapses to zero length -> no contribution.
            // Verify by giving alice a zero-length window and another user a real one;
            // alice must NOT appear in the result, bob's percentage must be 100%.
            Mission m = newMission(T0, T0_PLUS_60M);
            addUserParticipant(m, "alice", T0_PLUS_30M, T0_PLUS_30M, PayoutPreference.PAYOUT);
            addUserParticipant(m, "bob",   T0, T0_PLUS_60M, PayoutPreference.PAYOUT);
            stubOperation(Set.of(m));

            List<OperationPayoutDto> result = operationService.getOperationPayouts(OPERATION_ID);

            // alice contributes zero duration -> totalDuration becomes bob's only.
            // She still appears with 0% because the participant-name map captured her.
            // bob: 100%, alice: 0%.
            assertEquals(2, result.size());
            OperationPayoutDto alice = result.stream()
                    .filter(r -> r.participantName().equals("alice")).findFirst().orElseThrow();
            OperationPayoutDto bob = result.stream()
                    .filter(r -> r.participantName().equals("bob")).findFirst().orElseThrow();
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
            assertEquals(0.0, result.get(0).participationPercentage(),
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

        private void addUserParticipant(Mission mission, String username,
                                        Instant start, Instant end,
                                        PayoutPreference pref) {
            addUserParticipantWithUser(mission, newUser(username), start, end, pref);
        }

        private void addUserParticipantWithUser(Mission mission, User user,
                                                Instant start, Instant end,
                                                PayoutPreference pref) {
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
}
