package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.PromotionLevelContentMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionCategory;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevel;
import de.greluc.krt.iri.basetool.backend.model.PromotionLevelContent;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionLevelContentResponse;
import de.greluc.krt.iri.basetool.backend.repository.PromotionCategoryRepository;
import de.greluc.krt.iri.basetool.backend.repository.PromotionLevelContentRepository;
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

@ExtendWith(MockitoExtension.class)
class PromotionLevelContentServiceTest {

  @Mock private PromotionLevelContentRepository repository;

  @Mock private PromotionCategoryRepository categoryRepository;

  @Mock private PromotionLevelContentMapper mapper;

  @Mock private OwnerScopeService ownerScopeService;

  /**
   * Default-on the per-squadron promotion-feature flag so the existing fixtures that exercise the
   * "happy path" do not get short-circuited to empty by the new gate. {@code lenient()} keeps
   * Mockito from failing tests that never trigger the gate.
   */
  @BeforeEach
  void enablePromotionFeatureFlag() {
    lenient().when(ownerScopeService.isPromotionFeatureEnabledForCurrentScope()).thenReturn(true);
  }

  @InjectMocks private PromotionLevelContentService service;

  @Test
  void listByCategory_shouldReturnMappedContents() {
    // Given
    UUID categoryId = UUID.randomUUID();
    PromotionCategory category =
        PromotionCategory.builder().name("Flug Kenntnisse").sortOrder(0).build();
    PromotionLevelContent content =
        PromotionLevelContent.builder()
            .category(category)
            .level(PromotionLevel.LEVEL_A)
            .description("Kann fliegen")
            .build();
    PromotionLevelContentResponse response =
        new PromotionLevelContentResponse(
            UUID.randomUUID(),
            0L,
            categoryId,
            "Flug Kenntnisse",
            PromotionLevel.LEVEL_A,
            "Kann fliegen",
            null,
            null);
    when(repository.findAllByCategoryIdOrderByLevel(categoryId)).thenReturn(List.of(content));
    when(mapper.toResponse(content)).thenReturn(response);

    // When
    List<PromotionLevelContentResponse> result = service.listByCategory(categoryId);

    // Then
    assertEquals(1, result.size());
    assertEquals(PromotionLevel.LEVEL_A, result.get(0).level());
  }

  @Test
  void create_shouldThrow_whenCategoryNotFound() {
    // Given
    UUID categoryId = UUID.randomUUID();
    PromotionLevelContentCreateRequest request =
        new PromotionLevelContentCreateRequest(categoryId, PromotionLevel.LEVEL_A, "Beschreibung");
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(EntityNotFoundException.class, () -> service.create(request));
  }

  @Test
  void create_shouldSaveAndReturn() {
    // Given
    UUID categoryId = UUID.randomUUID();
    PromotionCategory category =
        PromotionCategory.builder().name("Flug Kenntnisse").sortOrder(0).build();
    PromotionLevelContentCreateRequest request =
        new PromotionLevelContentCreateRequest(categoryId, PromotionLevel.LEVEL_A, "Beschreibung");
    PromotionLevelContent entity =
        PromotionLevelContent.builder()
            .level(PromotionLevel.LEVEL_A)
            .description("Beschreibung")
            .build();
    PromotionLevelContentResponse response =
        new PromotionLevelContentResponse(
            UUID.randomUUID(),
            0L,
            categoryId,
            "Flug Kenntnisse",
            PromotionLevel.LEVEL_A,
            "Beschreibung",
            null,
            null);
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(mapper.toEntity(request)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(response);

    // When
    PromotionLevelContentResponse result = service.create(request);

    // Then
    assertEquals(PromotionLevel.LEVEL_A, result.level());
    verify(repository).save(entity);
  }

  @Test
  void delete_shouldCallRepositoryDelete() {
    // Given
    UUID id = UUID.randomUUID();
    PromotionLevelContent entity =
        PromotionLevelContent.builder().level(PromotionLevel.LEVEL_B).description("Test").build();
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When
    service.delete(id);

    // Then
    verify(repository).delete(entity);
  }

  @Test
  void get_shouldThrow_whenNotFound() {
    // Given
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(EntityNotFoundException.class, () -> service.get(id));
  }
}
