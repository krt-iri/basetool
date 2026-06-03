package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.RankRequirementMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionTopicRepository;
import de.greluc.krt.iri.basetool.backend.repository.RankRequirementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RankRequirementServiceTest {

  @Mock private RankRequirementRepository repository;

  @Mock private PromotionTopicRepository topicRepository;

  @Mock private PromotionCategoryRepository categoryRepository;

  @Mock private RankRequirementMapper mapper;

  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private RankRequirementService service;

  private static final UUID SQUADRON_ID = UUID.randomUUID();

  /**
   * Default-on the per-squadron promotion-feature flag so the existing fixtures that exercise the
   * "happy path" do not get short-circuited to empty by the new gate. {@code lenient()} keeps
   * Mockito from failing tests that never trigger the gate.
   */
  @BeforeEach
  void enablePromotionFeatureFlag() {
    lenient().when(ownerScopeService.isPromotionFeatureEnabledForCurrentScope()).thenReturn(true);
  }

  private static Squadron squadron(UUID id) {
    Squadron s = new Squadron();
    s.setId(id);
    s.setName("Test");
    s.setShorthand("TST");
    return s;
  }

  private static RankRequirementResponse anyResponse() {
    return new RankRequirementResponse(
        UUID.randomUUID(),
        0L,
        20,
        19,
        null,
        null,
        null,
        null,
        PromotionLevel.LEVEL_A,
        3,
        null,
        null,
        null);
  }

  @Test
  void list_shouldScopeByCurrentSquadron() {
    // Given
    Pageable pageable = PageRequest.of(0, 20);
    RankRequirement req =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    when(ownerScopeService.currentSquadronId()).thenReturn(Optional.of(SQUADRON_ID));
    when(repository.findAllScoped(SQUADRON_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(req), pageable, 1));
    when(mapper.toResponse(req)).thenReturn(anyResponse());

    // When
    service.list(pageable);

    // Then: the active squadron is passed straight to the scoped finder.
    verify(repository).findAllScoped(SQUADRON_ID, pageable);
    verify(repository, never()).findAll(any(Pageable.class));
  }

  @Test
  void listByRanks_shouldReturnMappedRequirementsScopedToSquadron() {
    // Given
    RankRequirement req =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    when(ownerScopeService.currentSquadronId()).thenReturn(Optional.of(SQUADRON_ID));
    when(repository.findScopedByFromRankAndToRank(20, 19, SQUADRON_ID)).thenReturn(List.of(req));
    when(mapper.toResponse(req)).thenReturn(anyResponse());

    // When
    List<RankRequirementResponse> result = service.listByRanks(20, 19);

    // Then
    assertEquals(1, result.size());
    verify(repository).findScopedByFromRankAndToRank(20, 19, SQUADRON_ID);
  }

  @Test
  void create_shouldStampOwningSquadronAndSave_global() {
    // Given: a "global" requirement (no topic, no category) — valid, scoped to the active squadron.
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(20, 19, null, null, PromotionLevel.LEVEL_A, 3, "Global");
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    Squadron squadron = squadron(SQUADRON_ID);
    when(ownerScopeService.currentSquadron()).thenReturn(Optional.of(squadron));
    when(mapper.toEntity(request)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(anyResponse());

    // When
    service.create(request);

    // Then: the owner is stamped from the active squadron context.
    assertSame(squadron, entity.getOwningSquadron());
    verify(repository).save(entity);
  }

  @Test
  void create_shouldRejectWhenNoActiveSquadron() {
    // Given: admin in "all squadrons" mode (no pin) → no squadron to stamp.
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(20, 19, null, null, PromotionLevel.LEVEL_A, 3, null);
    when(ownerScopeService.currentSquadron()).thenReturn(Optional.empty());

    // When / Then
    assertThrows(BadRequestException.class, () -> service.create(request));
    verify(repository, never()).save(any());
  }

  @Test
  void create_shouldRejectCrossSquadronTopicReference() {
    // Given: the caller is scoped to SQUADRON_ID but references a topic owned by another squadron.
    UUID foreignSquadronId = UUID.randomUUID();
    PromotionTopic foreignTopic = new PromotionTopic();
    foreignTopic.setId(UUID.randomUUID());
    foreignTopic.setOwningSquadron(squadron(foreignSquadronId));
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(
            20, 19, foreignTopic.getId(), null, PromotionLevel.LEVEL_A, 3, null);
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    when(ownerScopeService.currentSquadron()).thenReturn(Optional.of(squadron(SQUADRON_ID)));
    when(mapper.toEntity(request)).thenReturn(entity);
    when(topicRepository.findById(foreignTopic.getId())).thenReturn(Optional.of(foreignTopic));

    // When / Then
    assertThrows(BadRequestException.class, () -> service.create(request));
    verify(repository, never()).save(any());
  }

  @Test
  void create_shouldRejectMultiStepPromotion() {
    // Given
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(20, 18, null, null, PromotionLevel.LEVEL_A, 1, null);

    // When / Then
    BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request));
    assertEquals("error.rank_requirement.invalid_step", ex.getMessage());
    verifyNoInteractions(repository, mapper, topicRepository, categoryRepository);
  }

  @Test
  void create_shouldRejectReversePromotion() {
    // Given: lower-numbered fromRank than toRank (would be a demotion)
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(19, 20, null, null, PromotionLevel.LEVEL_A, 1, null);

    // When / Then
    assertThrows(BadRequestException.class, () -> service.create(request));
    verifyNoInteractions(repository, mapper, topicRepository, categoryRepository);
  }

  @Test
  void get_shouldThrow_whenNotFound() {
    // Given
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(EntityNotFoundException.class, () -> service.get(id));
  }

  @Test
  void get_shouldRejectCrossSquadron() {
    // Given: a requirement owned by a squadron the caller may not see.
    UUID id = UUID.randomUUID();
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    entity.setOwningSquadron(squadron(SQUADRON_ID));
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(ownerScopeService.canSeeSquadron(SQUADRON_ID)).thenReturn(false);

    // When / Then
    assertThrows(AccessDeniedException.class, () -> service.get(id));
  }

  @Test
  void update_shouldThrow_whenVersionMismatch() {
    // Given
    UUID id = UUID.randomUUID();
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    entity.setOwningSquadron(squadron(SQUADRON_ID));
    entity.setVersion(1L);
    var request =
        new RankRequirementUpdateRequest(0L, 20, 19, null, null, PromotionLevel.LEVEL_A, 3, null);
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(ownerScopeService.canEditSquadron(SQUADRON_ID)).thenReturn(true);

    // When / Then
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.update(id, request));
  }

  @Test
  void update_shouldRejectMultiStepPromotion() {
    // Given
    UUID id = UUID.randomUUID();
    var request =
        new RankRequirementUpdateRequest(0L, 20, 18, null, null, PromotionLevel.LEVEL_A, 1, null);

    // When / Then
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> service.update(id, request));
    assertEquals("error.rank_requirement.invalid_step", ex.getMessage());
    // The repository must not even be hit when input validation fails.
    verifyNoInteractions(repository, mapper, topicRepository, categoryRepository);
  }

  @Test
  void delete_shouldCallRepositoryDelete() {
    // Given
    UUID id = UUID.randomUUID();
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    entity.setOwningSquadron(squadron(SQUADRON_ID));
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(ownerScopeService.canEditSquadron(SQUADRON_ID)).thenReturn(true);

    // When
    service.delete(id);

    // Then
    verify(repository).delete(entity);
  }

  @Test
  void delete_shouldRejectCrossSquadron() {
    // Given: a requirement owned by a squadron the caller may not edit.
    UUID id = UUID.randomUUID();
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    entity.setOwningSquadron(squadron(SQUADRON_ID));
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(ownerScopeService.canEditSquadron(SQUADRON_ID)).thenReturn(false);

    // When / Then
    assertThrows(AccessDeniedException.class, () -> service.delete(id));
    verify(repository, never()).delete(any());
  }
}
