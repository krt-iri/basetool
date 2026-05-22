package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.mapper.RankRequirementMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.RankRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementResponse;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class RankRequirementServiceTest {

  @Mock private RankRequirementRepository repository;

  @Mock private PromotionTopicRepository topicRepository;

  @Mock private PromotionCategoryRepository categoryRepository;

  @Mock private RankRequirementMapper mapper;

  @Mock private OwnerScopeService ownerScopeService;

  /**
   * Default-on the per-squadron promotion-feature flag so the existing fixtures that exercise the
   * "happy path" do not get short-circuited to empty by the new gate. {@code lenient()} keeps
   * Mockito from failing tests that never trigger the gate.
   */
  @BeforeEach
  void enablePromotionFeatureFlag() {
    lenient()
        .when(ownerScopeService.isPromotionFeatureEnabledForCurrentScope())
        .thenReturn(true);
  }

  @InjectMocks private RankRequirementService service;

  @Test
  void listByRanks_shouldReturnMappedRequirements() {
    // Given
    RankRequirement req =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    RankRequirementResponse response =
        new RankRequirementResponse(
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
    when(repository.findAllByFromRankAndToRankOrderByIdAsc(20, 19)).thenReturn(List.of(req));
    when(mapper.toResponse(req)).thenReturn(response);

    // When
    List<RankRequirementResponse> result = service.listByRanks(20, 19);

    // Then
    assertEquals(1, result.size());
    assertEquals(20, result.get(0).fromRank());
    assertEquals(19, result.get(0).toRank());
  }

  @Test
  void create_shouldSaveWithNullTopicAndCategory() {
    // Given
    RankRequirementCreateRequest request =
        new RankRequirementCreateRequest(
            20, 19, null, null, PromotionLevel.LEVEL_A, 3, "Grundlagen");
    RankRequirement entity =
        RankRequirement.builder()
            .fromRank(20)
            .toRank(19)
            .minimumLevel(PromotionLevel.LEVEL_A)
            .requiredCount(3)
            .build();
    RankRequirementResponse response =
        new RankRequirementResponse(
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
            "Grundlagen",
            null,
            null);
    when(mapper.toEntity(request)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(response);

    // When
    RankRequirementResponse result = service.create(request);

    // Then
    assertEquals(20, result.fromRank());
    verify(repository).save(entity);
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
    entity.setVersion(1L);
    var request =
        new de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementUpdateRequest(
            0L, 20, 19, null, null, PromotionLevel.LEVEL_A, 3, null);
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When / Then
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.update(id, request));
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
  void update_shouldRejectMultiStepPromotion() {
    // Given
    UUID id = UUID.randomUUID();
    var request =
        new de.greluc.krt.iri.basetool.backend.model.dto.RankRequirementUpdateRequest(
            0L, 20, 18, null, null, PromotionLevel.LEVEL_A, 1, null);

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
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When
    service.delete(id);

    // Then
    verify(repository).delete(entity);
  }
}
