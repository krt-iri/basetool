package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionOwnership;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionOwnershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer die Section-Patch-Methoden in {@link MissionService}.
 *
 * <p>Verifiziert, dass:
 * <ul>
 *   <li>ein erfolgreicher Section-Patch nur die Felder der jeweiligen Sektion aktualisiert,</li>
 *   <li>bei abweichender {@code expectedVersion} eine
 *       {@link ObjectOptimisticLockingFailureException} (HTTP 409) geworfen wird,</li>
 *   <li>die Zeitplan-Validierung (meeting &le; plannedStart &le; plannedEnd) weiterhin greift.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceSectionPatchTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionOwnershipRepository missionOwnershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MissionService missionService;

    private UUID missionId;
    private Mission existing;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        existing = new Mission();
        existing.setId(missionId);
        existing.setVersion(7L);
        existing.setName("Old name");
        existing.setDescription("Old desc");
        existing.setIsInternal(false);
        existing.setPlannedStartTime(Instant.parse("2030-01-01T10:00:00Z"));
        existing.setPlannedEndTime(Instant.parse("2030-01-01T12:00:00Z"));
    }

    @Test
    void updateCoreSection_shouldUpdateOnlyCoreFields_whenVersionMatches() {
        // Given
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Mission result = missionService.updateCoreSection(missionId, "New name", "New desc",
                "https://example.org/cal", "PLANNED", 7L);

        // Then
        assertEquals("New name", result.getName());
        assertEquals("New desc", result.getDescription());
        assertEquals("https://example.org/cal", result.getCalendarLink());
        assertEquals("PLANNED", result.getStatus());
        // Schedule-/Flags-Felder bleiben unveraendert
        assertEquals(Instant.parse("2030-01-01T10:00:00Z"), result.getPlannedStartTime());
        assertFalse(result.getIsInternal());
    }

    @Test
    void updateCoreSection_shouldThrow409_whenVersionMismatch() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> missionService.updateCoreSection(missionId, "X", null, null, null, 6L));
    }

    @Test
    void updateScheduleSection_shouldUpdateOnlyScheduleFields_whenVersionMatches() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant meeting = Instant.parse("2030-01-01T09:45:00Z");
        Instant plannedStart = Instant.parse("2030-01-01T10:00:00Z");
        Instant plannedEnd = plannedStart.plus(2, ChronoUnit.HOURS);

        Mission result = missionService.updateScheduleSection(missionId, meeting, plannedStart,
                plannedEnd, null, null, 7L);

        assertEquals(meeting, result.getMeetingTime());
        assertEquals(plannedStart, result.getPlannedStartTime());
        assertEquals(plannedEnd, result.getPlannedEndTime());
        // Core-Felder bleiben unveraendert
        assertEquals("Old name", result.getName());
    }

    @Test
    void updateScheduleSection_shouldRejectInvalidTimeOrder() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));

        Instant plannedStart = Instant.parse("2030-01-01T12:00:00Z");
        Instant plannedEnd = Instant.parse("2030-01-01T10:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> missionService.updateScheduleSection(missionId, null, plannedStart, plannedEnd,
                        null, null, 7L));
    }

    @Test
    void updateFlagsSection_shouldFlipInternalFlag_whenVersionMatches() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        Mission result = missionService.updateFlagsSection(missionId, true, 7L);

        assertTrue(result.getIsInternal());
        // Core unveraendert
        assertEquals("Old name", result.getName());
    }

    @Test
    void updateFlagsSection_shouldThrow409_whenVersionMismatch() {
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> missionService.updateFlagsSection(missionId, true, 999L));
    }

    // -----------------------------------------------------------------------------------------
    // Option A / multi-user concurrency: sub-section writes MUST NOT call missionRepository.save(mission)
    // and MUST NOT bump the parent Mission.version. These tests lock in that guarantee for the
    // most impactful sub-section paths.
    // -----------------------------------------------------------------------------------------

    @Test
    void removeMissionUnit_shouldNotCallMissionRepositorySave() {
        // Given an existing mission with a unit
        de.greluc.krt.iri.basetool.backend.model.MissionUnit unit =
                new de.greluc.krt.iri.basetool.backend.model.MissionUnit();
        UUID unitId = UUID.randomUUID();
        unit.setId(unitId);
        existing.getAssignedUnits().add(unit);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));

        // When
        Mission result = missionService.removeMissionUnit(missionId, unitId);

        // Then: parent is returned but was never persisted via save(mission)
        assertSame(existing, result);
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    void removeManager_shouldNotCallMissionRepositorySave() {
        UUID userId = UUID.randomUUID();
        User manager = new User();
        manager.setId(userId);
        existing.getManagers().add(manager);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));

        Mission result = missionService.removeManager(missionId, userId);

        assertSame(existing, result);
        verify(missionRepository, never()).save(any(Mission.class));
    }

    // -----------------------------------------------------------------------------------------
    // MissionOwnership (Option A, variant a): owner changes are protected by a dedicated
    // optimistic-lock version on the MissionOwnership companion row; Mission.version is NOT bumped.
    // -----------------------------------------------------------------------------------------

    @Test
    void updateMissionOwner_shouldSucceed_whenOwnershipVersionMatches() {
        // Given
        UUID newOwnerId = UUID.randomUUID();
        User newOwner = new User();
        newOwner.setId(newOwnerId);

        MissionOwnership ownership = new MissionOwnership();
        ownership.setId(UUID.randomUUID());
        ownership.setMission(existing);
        ownership.setVersion(3L);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));
        when(userRepository.findById(newOwnerId)).thenReturn(Optional.of(newOwner));
        when(missionOwnershipRepository.findByMissionId(missionId)).thenReturn(Optional.of(ownership));
        when(missionOwnershipRepository.save(any(MissionOwnership.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Mission result = missionService.updateMissionOwner(missionId, newOwnerId, 3L);

        // Then
        assertSame(existing, result);
        assertNotNull(result.getOwner());
        assertEquals(newOwnerId, result.getOwner().getId());
        // Crucial: Mission.version was NOT bumped via save(mission)
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    void updateMissionOwner_shouldThrow409_whenOwnershipVersionMismatch() {
        UUID newOwnerId = UUID.randomUUID();
        User newOwner = new User();
        newOwner.setId(newOwnerId);

        MissionOwnership ownership = new MissionOwnership();
        ownership.setId(UUID.randomUUID());
        ownership.setMission(existing);
        ownership.setVersion(3L);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(existing));
        when(userRepository.findById(newOwnerId)).thenReturn(Optional.of(newOwner));
        when(missionOwnershipRepository.findByMissionId(missionId)).thenReturn(Optional.of(ownership));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> missionService.updateMissionOwner(missionId, newOwnerId, 2L));
    }

    @Test
    void getMissionOwnershipVersion_shouldReturnZero_whenNoRowYet() {
        when(missionOwnershipRepository.findByMissionId(missionId)).thenReturn(Optional.empty());

        long v = missionService.getMissionOwnershipVersion(missionId);

        assertEquals(0L, v);
    }

    @Test
    void getMissionOwnershipVersion_shouldReturnCurrentVersion() {
        MissionOwnership ownership = new MissionOwnership();
        ownership.setId(UUID.randomUUID());
        ownership.setMission(existing);
        ownership.setVersion(5L);
        when(missionOwnershipRepository.findByMissionId(missionId)).thenReturn(Optional.of(ownership));

        long v = missionService.getMissionOwnershipVersion(missionId);

        assertEquals(5L, v);
    }
}
