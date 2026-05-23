package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.UpdateMissionRequest;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import de.greluc.krt.iri.basetool.backend.service.ScopePredicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

  @Mock private MissionRepository missionRepository;

  @Mock private UserRepository userRepository;

  @Mock private SquadronRepository squadronRepository;

  @Mock private MissionParticipantRepository missionParticipantRepository;

  @Mock private de.greluc.krt.iri.basetool.backend.service.OwnerScopeService ownerScopeService;

  @Mock private de.greluc.krt.iri.basetool.backend.service.UserService userService;

  @Mock private de.greluc.krt.iri.basetool.backend.service.AuthHelperService authHelperService;

  @InjectMocks private MissionService missionService;

  @Test
  void addParticipant_ShouldMatchGuestNameToExistingUserCaseInsensitive() {
    UUID missionId = UUID.randomUUID();
    Mission mission = new Mission();
    mission.setId(missionId);

    UUID userId = UUID.randomUUID();
    User existingUser = new User();
    existingUser.setId(userId);
    existingUser.setUsername("testuser");

    when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
    when(userRepository.findByUsernameIgnoreCaseOrDisplayNameIgnoreCase("TestUser", "TestUser"))
        .thenReturn(Optional.of(existingUser));
    when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
    // Post-fix #10 fallback path: when the resolved user has no squadron, MissionService
    // looks up IRIDIUM by its canonical UUID (not by shorthand) and stamps the participant.
    when(squadronRepository.findById(Squadron.IRIDIUM_ID)).thenReturn(Optional.of(new Squadron()));

    Mission result = missionService.addParticipant(missionId, null, "TestUser", null, "No comment");

    assertEquals(1, result.getParticipants().size());
    MissionParticipant participant = result.getParticipants().iterator().next();
    assertNotNull(participant.getUser(), "User should be mapped from guest name");
    assertEquals(userId, participant.getUser().getId());
    assertNull(participant.getGuestName(), "Guest name should be nullified since user was found");
  }

  @Test
  void searchMissions_ShouldCallRepository() {
    String query = "Test";
    Instant start = Instant.now();
    Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
    List<String> status =
        List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"); // Default expected when null passed

    // M-1: searchMissions now forces {@code isInternal=false} for anonymous callers. This
    // Mockito unit test runs with no SecurityContext (anonymous), so the service rewrites the
    // {@code null} input to {@code Boolean.FALSE} before delegating to the repository.
    Pageable pageable = PageRequest.of(0, 10);
    when(ownerScopeService.currentScopePredicate())
        .thenReturn(new ScopePredicate(false, null, Set.of()));
    when(missionRepository.searchMissions(
            query, start, end, status, Boolean.FALSE, null, false, null, Set.of(), pageable))
        .thenReturn(Page.empty());

    missionService.searchMissions(query, start, end, null, null, null, pageable);

    verify(missionRepository)
        .searchMissions(
            query, start, end, status, Boolean.FALSE, null, false, null, Set.of(), pageable);
  }

  @Test
  void updateMission_ShouldSetActualStartTime_WhenStatusChangesToActive() {
    UUID id = UUID.randomUUID();
    Mission existing = new Mission();
    existing.setId(id);
    existing.setStatus("PLANNED");
    existing.setActualStartTime(null);
    existing.setVersion(0L);

    UpdateMissionRequest details =
        new UpdateMissionRequest(
            "Test", null, null, "ACTIVE", null, null, null, null, null, null, null, 0L);

    when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission updated = missionService.updateMission(id, details);

    assertNotNull(updated.getActualStartTime());
  }

  @Test
  void updateMission_ShouldUseProvidedActualStartTime() {
    UUID id = UUID.randomUUID();
    Mission existing = new Mission();
    existing.setId(id);
    existing.setStatus("PLANNED");
    existing.setVersion(0L);

    Instant manualStart = Instant.now().minus(1, ChronoUnit.HOURS);
    UpdateMissionRequest details =
        new UpdateMissionRequest(
            "Test", null, null, "ACTIVE", null, null, null, manualStart, null, null, null, 0L);

    when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission updated = missionService.updateMission(id, details);

    assertEquals(manualStart, updated.getActualStartTime());
  }

  @Test
  void updateMission_ShouldNotSetActualStartTime_WhenStatusIsActiveButAlreadyActive() {
    UUID id = UUID.randomUUID();
    Mission existing = new Mission();
    existing.setId(id);
    existing.setStatus("ACTIVE");
    existing.setActualStartTime(null);
    existing.setVersion(0L);

    UpdateMissionRequest details =
        new UpdateMissionRequest(
            "Test", null, null, "ACTIVE", null, null, null, null, null, null, null, 0L);

    when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission updated = missionService.updateMission(id, details);

    assertNull(updated.getActualStartTime());
  }

  @Test
  void updateMission_ShouldUpdateCalendarLink() {
    UUID id = UUID.randomUUID();
    Mission existing = new Mission();
    existing.setId(id);
    existing.setCalendarLink("old-link");
    existing.setVersion(0L);

    UpdateMissionRequest details =
        new UpdateMissionRequest(
            "Test", null, "new-link", "PLANNED", null, null, null, null, null, null, null, 0L);

    when(missionRepository.findById(id)).thenReturn(Optional.of(existing));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission updated = missionService.updateMission(id, details);

    assertEquals("new-link", updated.getCalendarLink());
  }

  @Test
  void createMission_ShouldThrowException_WhenMeetingTimeAfterPlannedStart() {
    Instant plannedStart = Instant.now();
    CreateMissionRequest request =
        new CreateMissionRequest(
            "Test",
            null,
            null,
            null,
            plannedStart.plus(1, ChronoUnit.HOURS),
            plannedStart,
            null,
            false,
            null,
            null);

    assertThrows(IllegalArgumentException.class, () -> missionService.createMission(request));
  }

  @Test
  void createMission_ShouldThrowException_WhenPlannedStartAfterPlannedEnd() {
    Instant now = Instant.now();
    CreateMissionRequest request =
        new CreateMissionRequest(
            "Test",
            null,
            null,
            null,
            null,
            now.plus(2, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            false,
            null,
            null);

    assertThrows(IllegalArgumentException.class, () -> missionService.createMission(request));
  }

  // ── Audit finding C-4: server-side stamping of owningSquadron ────────
  // Pins the create-mission stamping pipeline so a future refactor that re-introduces a
  // client-supplied owningSquadron path (e.g. by adding a `UUID owningSquadronId` field to
  // CreateMissionRequest and threading it into MissionService) breaks here. The ArchUnit rule
  // {@code missionWriteRequestDtosMustNotCarryServerManagedFields} blocks the DTO-shape side; the
  // tests below pin the service-side stamping behaviour itself.

  @Test
  void createMission_stampsOwningSquadronFromOwnerSquadron() {
    Squadron home = new Squadron();
    home.setId(UUID.randomUUID());
    User caller = new User();
    caller.setId(UUID.randomUUID());
    caller.setSquadron(home);

    when(userService.getCurrentUser()).thenReturn(Optional.of(caller));
    when(ownerScopeService.resolveSquadronForPickerOutput(caller, null)).thenReturn(home);
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission saved =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test", null, null, "PLANNED", null, null, null, false, null, null));

    assertEquals(home, saved.getOwningSquadron());
    assertEquals(caller, saved.getOwner());
  }

  @Test
  void createMission_fallsBackToCurrentSquadronScopeWhenNoOwnerResolved() {
    Squadron scopeSquadron = new Squadron();
    scopeSquadron.setId(UUID.randomUUID());

    // R5.d.d branch flip: the fallback fires when getCurrentUser() returns empty, not when the
    // owner has no home Staffel. An authenticated owner without a home Staffel now flows through
    // OwnerScopeService.resolveSquadronForPickerOutput and inherits whatever that returns.
    when(userService.getCurrentUser()).thenReturn(Optional.empty());
    when(ownerScopeService.currentSquadron()).thenReturn(Optional.of(scopeSquadron));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission saved =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test", null, null, "PLANNED", null, null, null, false, null, null));

    assertEquals(scopeSquadron, saved.getOwningSquadron());
  }

  @Test
  void createMission_honoursOwningOrgUnitIdFromTheRequestViaPickerResolver() {
    Squadron home = new Squadron();
    home.setId(UUID.randomUUID());
    User caller = new User();
    caller.setId(UUID.randomUUID());
    caller.setSquadron(home);
    Squadron picked = new Squadron();
    picked.setId(UUID.randomUUID());

    when(userService.getCurrentUser()).thenReturn(Optional.of(caller));
    when(ownerScopeService.resolveSquadronForPickerOutput(caller, picked.getId()))
        .thenReturn(picked);
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission saved =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test", null, null, "PLANNED", null, null, null, false, null, picked.getId()));

    assertEquals(picked, saved.getOwningSquadron(), "picker output must be honoured verbatim");
  }

  @Test
  void addSubMission_inheritsOwningSquadronFromParent_ignoringScopeAndCaller() {
    Squadron parentSquadron = new Squadron();
    parentSquadron.setId(UUID.randomUUID());
    UUID parentId = UUID.randomUUID();
    Mission parent = new Mission();
    parent.setId(parentId);
    parent.setOwningSquadron(parentSquadron);

    when(missionRepository.findById(parentId)).thenReturn(Optional.of(parent));
    when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArguments()[0]);

    Mission saved =
        missionService.addSubMission(
            parentId,
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Sub", null, null, "PLANNED", null, null, null, false, null, null));

    assertEquals(parentSquadron, saved.getOwningSquadron());
    assertEquals(parent, saved.getParent());
  }
}
