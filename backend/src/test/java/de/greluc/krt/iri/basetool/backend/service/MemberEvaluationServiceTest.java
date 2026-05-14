package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.MemberEvaluationMapper;
import de.greluc.krt.iri.basetool.backend.model.MemberEvaluation;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.dto.MemberEvaluationResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.MemberEvaluationUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.MemberEvaluationRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class MemberEvaluationServiceTest {

  @Mock private MemberEvaluationRepository repository;

  @Mock private PromotionCategoryRepository categoryRepository;

  @Mock private MemberEvaluationMapper mapper;

  @InjectMocks private MemberEvaluationService service;

  @Test
  void listForUser_shouldOnlyReturnOwnEvaluations() {
    // Given – data isolation: only evaluations for "user-A" are returned
    String userA = "user-A";
    String userB = "user-B";
    MemberEvaluation evalA =
        MemberEvaluation.builder().userId(userA).assignedLevel(PromotionLevel.LEVEL_A).build();
    MemberEvaluationResponse responseA =
        new MemberEvaluationResponse(
            UUID.randomUUID(),
            0L,
            userA,
            UUID.randomUUID(),
            "Cat",
            UUID.randomUUID(),
            "Topic",
            PromotionLevel.LEVEL_A,
            null,
            null);
    when(repository.findAllByUserId(userA)).thenReturn(List.of(evalA));
    when(mapper.toResponse(evalA)).thenReturn(responseA);

    // When
    List<MemberEvaluationResponse> result = service.listForUser(userA);

    // Then – only user-A's evaluations are returned, user-B's are never fetched
    assertEquals(1, result.size());
    assertEquals(userA, result.get(0).userId());
    verify(repository, never()).findAllByUserId(userB);
  }

  @Test
  void upsert_shouldCreateNewEvaluation_whenNoneExists() {
    // Given
    String userId = "user-A";
    UUID categoryId = UUID.randomUUID();
    PromotionCategory category =
        PromotionCategory.builder().name("Flug Kenntnisse").sortOrder(0).build();
    MemberEvaluationUpdateRequest request =
        new MemberEvaluationUpdateRequest(null, PromotionLevel.LEVEL_B);
    MemberEvaluation saved =
        MemberEvaluation.builder()
            .userId(userId)
            .category(category)
            .assignedLevel(PromotionLevel.LEVEL_B)
            .build();
    MemberEvaluationResponse response =
        new MemberEvaluationResponse(
            UUID.randomUUID(),
            0L,
            userId,
            categoryId,
            "Flug Kenntnisse",
            UUID.randomUUID(),
            "Grundlagen",
            PromotionLevel.LEVEL_B,
            null,
            null);
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(repository.findByUserIdAndCategoryId(userId, categoryId)).thenReturn(Optional.empty());
    when(repository.save(any(MemberEvaluation.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    // When
    MemberEvaluationResponse result = service.upsert(userId, categoryId, request);

    // Then
    assertEquals(PromotionLevel.LEVEL_B, result.assignedLevel());
    verify(repository).save(any(MemberEvaluation.class));
  }

  @Test
  void upsert_shouldThrow_whenVersionMismatch() {
    // Given
    String userId = "user-A";
    UUID categoryId = UUID.randomUUID();
    UUID evalId = UUID.randomUUID();
    PromotionCategory category =
        PromotionCategory.builder().name("Flug Kenntnisse").sortOrder(0).build();
    MemberEvaluation existing =
        MemberEvaluation.builder()
            .userId(userId)
            .category(category)
            .assignedLevel(PromotionLevel.LEVEL_A)
            .build();
    existing.setId(evalId);
    existing.setVersion(1L);
    MemberEvaluationUpdateRequest request =
        new MemberEvaluationUpdateRequest(0L, PromotionLevel.LEVEL_B);
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(repository.findByUserIdAndCategoryId(userId, categoryId))
        .thenReturn(Optional.of(existing));

    // When / Then
    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> service.upsert(userId, categoryId, request));
  }

  @Test
  void upsert_shouldThrow_whenCategoryNotFound() {
    // Given
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(
        EntityNotFoundException.class,
        () ->
            service.upsert(
                "user-A",
                categoryId,
                new MemberEvaluationUpdateRequest(null, PromotionLevel.LEVEL_A)));
  }

  @Test
  void delete_shouldCallRepositoryDelete() {
    // Given
    UUID id = UUID.randomUUID();
    MemberEvaluation entity =
        MemberEvaluation.builder().userId("user-A").assignedLevel(PromotionLevel.LEVEL_A).build();
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When
    service.delete(id);

    // Then
    verify(repository).delete(entity);
  }
}
