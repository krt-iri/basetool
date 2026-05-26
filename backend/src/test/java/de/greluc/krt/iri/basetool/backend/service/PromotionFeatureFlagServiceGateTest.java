package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.PromotionTopicMapper;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionTopicRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Verifies the per-squadron promotion-feature gate end-to-end through {@link OwnerScopeService} +
 * the adjacent {@link PromotionTopicService} (the rest of the promotion services follow the same
 * pattern, so one representative is enough — every gated call site uses the same {@code
 * OwnerScopeService} primitive).
 *
 * <p>What's pinned here: admins bypass the gate (because they own the toggle), Officers / members
 * of a flag-off squadron get empty reads and {@link AccessDeniedException} on writes, and the
 * squadron-toggle service method flips only the flag without touching any other column.
 */
@ExtendWith(MockitoExtension.class)
class PromotionFeatureFlagServiceGateTest {

  @Mock private AuthHelperService authHelper;
  @Mock private SquadronRepository squadronRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private HttpServletRequest request;
  @Mock private UserRepository userRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private OperationRepository operationRepository;
  @Mock private ShipRepository shipRepository;

  // R2.c: the real flag-resolution logic moved from OwnerScopeService to OwnerScopeService;
  // we inject the latter directly with its repository mocks. The downstream PromotionTopicService
  // tests further down still receive the OwnerScopeService shim as a plain Mockito mock — the
  // shim's bean shape is unchanged from the caller's perspective.
  @InjectMocks private OwnerScopeService ownerScopeService;

  private static Squadron squadron(UUID id, boolean enabled) {
    Squadron s = new Squadron();
    s.setId(id);
    s.setName("Test");
    s.setShorthand("TST");
    s.setPromotionEnabled(enabled);
    return s;
  }

  @BeforeEach
  void stubMembershipLookup() {
    lenient().when(authHelper.isAdmin()).thenReturn(false);
  }

  @Test
  @DisplayName("Admin always bypasses the feature flag, even when the squadron has it OFF")
  void adminAlwaysPassesGate() {
    when(authHelper.isAdmin()).thenReturn(true);
    assertTrue(ownerScopeService.isPromotionFeatureEnabledForCurrentScope());
    ownerScopeService.assertPromotionFeatureEnabled();
  }

  @Test
  @DisplayName("Non-admin with squadron flag ON passes the gate")
  void nonAdminWithFlagOnPassesGate() {
    UUID userId = UUID.randomUUID();
    UUID squadronId = UUID.randomUUID();
    when(authHelper.currentUserId()).thenReturn(Optional.of(userId));
    User user = new User();
    user.setSquadron(squadron(squadronId, true));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(squadronRepository.findById(squadronId))
        .thenReturn(Optional.of(squadron(squadronId, true)));

    assertTrue(ownerScopeService.isPromotionFeatureEnabledForCurrentScope());
    ownerScopeService.assertPromotionFeatureEnabled();
  }

  @Test
  @DisplayName("Non-admin with squadron flag OFF fails the gate and assert throws 403")
  void nonAdminWithFlagOffFailsGate() {
    UUID userId = UUID.randomUUID();
    UUID squadronId = UUID.randomUUID();
    when(authHelper.currentUserId()).thenReturn(Optional.of(userId));
    User user = new User();
    user.setSquadron(squadron(squadronId, false));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(squadronRepository.findById(squadronId))
        .thenReturn(Optional.of(squadron(squadronId, false)));

    assertFalse(ownerScopeService.isPromotionFeatureEnabledForCurrentScope());
    AccessDeniedException ex =
        assertThrows(
            AccessDeniedException.class, () -> ownerScopeService.assertPromotionFeatureEnabled());
    assertTrue(ex.getMessage().toLowerCase().contains("promotion"));
  }

  @Test
  @DisplayName("Non-admin without an effective squadron defaults to enabled")
  void nonAdminWithoutSquadronDefaultsToEnabled() {
    when(authHelper.currentUserId()).thenReturn(Optional.empty());
    assertTrue(ownerScopeService.isPromotionFeatureEnabledForCurrentScope());
  }

  @Test
  @DisplayName("PromotionTopicService.list short-circuits to empty page when flag is OFF")
  void topicList_returnsEmptyWhenDisabled() {
    PromotionTopicRepository topicRepository = mock(PromotionTopicRepository.class);
    PromotionTopicMapper mapper = mock(PromotionTopicMapper.class);
    OwnerScopeService scopeStub = mock(OwnerScopeService.class);
    PromotionTopicService service = new PromotionTopicService(topicRepository, mapper, scopeStub);
    when(scopeStub.isPromotionFeatureEnabledForCurrentScope()).thenReturn(false);
    Pageable pageable = PageRequest.of(0, 20);

    Page<?> result = service.list(pageable);

    assertEquals(0, result.getTotalElements());
    verify(topicRepository, never()).findAllScoped(any(), any(Pageable.class));
  }

  @Test
  @DisplayName("PromotionTopicService.listAll short-circuits to empty list when flag is OFF")
  void topicListAll_returnsEmptyWhenDisabled() {
    PromotionTopicRepository topicRepository = mock(PromotionTopicRepository.class);
    PromotionTopicMapper mapper = mock(PromotionTopicMapper.class);
    OwnerScopeService scopeStub = mock(OwnerScopeService.class);
    PromotionTopicService service = new PromotionTopicService(topicRepository, mapper, scopeStub);
    when(scopeStub.isPromotionFeatureEnabledForCurrentScope()).thenReturn(false);

    assertTrue(service.listAll().isEmpty());
    verify(topicRepository, never()).findAllScoped(any());
  }

  @Test
  @DisplayName("PromotionTopicService.create throws AccessDenied when flag is OFF")
  void topicCreate_throwsWhenDisabled() {
    PromotionTopicRepository topicRepository = mock(PromotionTopicRepository.class);
    PromotionTopicMapper mapper = mock(PromotionTopicMapper.class);
    OwnerScopeService scopeStub = mock(OwnerScopeService.class);
    PromotionTopicService service = new PromotionTopicService(topicRepository, mapper, scopeStub);
    doThrow(new AccessDeniedException("disabled")).when(scopeStub).assertPromotionFeatureEnabled();

    assertThrows(
        AccessDeniedException.class,
        () -> service.create(new PromotionTopicCreateRequest("Name", null, 0)));
    verify(topicRepository, never()).save(any());
  }

  @Test
  @DisplayName("PromotionTopicService.update throws AccessDenied when flag is OFF — no load/save")
  void topicUpdate_throwsWhenDisabled() {
    PromotionTopicRepository topicRepository = mock(PromotionTopicRepository.class);
    PromotionTopicMapper mapper = mock(PromotionTopicMapper.class);
    OwnerScopeService scopeStub = mock(OwnerScopeService.class);
    PromotionTopicService service = new PromotionTopicService(topicRepository, mapper, scopeStub);
    doThrow(new AccessDeniedException("disabled")).when(scopeStub).assertPromotionFeatureEnabled();

    assertThrows(
        AccessDeniedException.class,
        () ->
            service.update(
                UUID.randomUUID(), new PromotionTopicUpdateRequest(0L, "Renamed", null, 0)));
    verify(topicRepository, never()).findById(any());
  }

  @Test
  @DisplayName("PromotionTopicService.delete throws AccessDenied when flag is OFF")
  void topicDelete_throwsWhenDisabled() {
    PromotionTopicRepository topicRepository = mock(PromotionTopicRepository.class);
    PromotionTopicMapper mapper = mock(PromotionTopicMapper.class);
    OwnerScopeService scopeStub = mock(OwnerScopeService.class);
    PromotionTopicService service = new PromotionTopicService(topicRepository, mapper, scopeStub);
    doThrow(new AccessDeniedException("disabled")).when(scopeStub).assertPromotionFeatureEnabled();

    assertThrows(AccessDeniedException.class, () -> service.delete(UUID.randomUUID()));
    verify(topicRepository, never()).findById(any());
  }

  @Test
  @DisplayName("SquadronService.setPromotionEnabled flips only the flag and persists")
  void squadronToggle_flipsOnlyFlag() {
    SquadronRepository repository = mock(SquadronRepository.class);
    MissionParticipantRepository participantRepository = mock(MissionParticipantRepository.class);
    SquadronService service = new SquadronService(repository, participantRepository);
    UUID id = UUID.randomUUID();
    Squadron entity = squadron(id, true);
    entity.setName("Original");
    entity.setShorthand("ORG");
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(repository.save(any(Squadron.class))).thenAnswer(inv -> inv.getArgument(0));

    Squadron updated = service.setPromotionEnabled(id, false);

    assertFalse(updated.isPromotionEnabled());
    assertEquals("Original", updated.getName(), "Toggle must not touch the name");
    assertEquals("ORG", updated.getShorthand(), "Toggle must not touch the shorthand");
  }

  @Test
  @DisplayName("SquadronService.setPromotionEnabled raises 404 for unknown squadron id")
  void squadronToggle_rejectsUnknownId() {
    SquadronRepository repository = mock(SquadronRepository.class);
    MissionParticipantRepository participantRepository = mock(MissionParticipantRepository.class);
    SquadronService service = new SquadronService(repository, participantRepository);
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.setPromotionEnabled(id, false));
  }

  @Test
  @DisplayName("SquadronService.setPromotionEnabled propagates optimistic-lock failures")
  void squadronToggle_propagatesOptimisticLockFailure() {
    SquadronRepository repository = mock(SquadronRepository.class);
    MissionParticipantRepository participantRepository = mock(MissionParticipantRepository.class);
    SquadronService service = new SquadronService(repository, participantRepository);
    UUID id = UUID.randomUUID();
    Squadron entity = squadron(id, true);
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(repository.save(any(Squadron.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Squadron.class, id));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> service.setPromotionEnabled(id, false));
  }
}
