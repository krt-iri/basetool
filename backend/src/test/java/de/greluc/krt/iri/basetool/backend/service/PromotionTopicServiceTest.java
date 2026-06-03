package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.mapper.PromotionTopicMapper;
import de.greluc.krt.iri.basetool.backend.model.PromotionTopic;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PromotionTopicUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.PromotionTopicRepository;
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
class PromotionTopicServiceTest {

  @Mock private PromotionTopicRepository repository;

  @Mock private PromotionTopicMapper mapper;

  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private PromotionTopicService service;

  /**
   * Default-on the per-squadron promotion-feature flag so the existing fixtures that exercise the
   * "happy path" do not get short-circuited to empty by the new gate. {@code lenient()} keeps
   * Mockito from failing tests that never trigger the gate (e.g. the validation paths that throw
   * before the gate check).
   */
  @BeforeEach
  void enablePromotionFeatureFlag() {
    lenient().when(ownerScopeService.isPromotionFeatureEnabledForCurrentScope()).thenReturn(true);
    lenient().when(ownerScopeService.hasPromotionReadAccess()).thenReturn(true);
  }

  @Test
  void listAll_shouldReturnMappedTopics() {
    // Given
    PromotionTopic topic = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    PromotionTopicResponse response =
        new PromotionTopicResponse(UUID.randomUUID(), 0L, "Grundlagen", null, 0, null, null, null);
    when(ownerScopeService.currentSquadronId()).thenReturn(Optional.empty());
    when(repository.findAllScoped((UUID) null)).thenReturn(List.of(topic));
    when(mapper.toResponse(topic)).thenReturn(response);

    // When
    List<PromotionTopicResponse> result = service.listAll();

    // Then
    assertEquals(1, result.size());
    assertEquals("Grundlagen", result.get(0).name());
  }

  @Test
  void get_shouldReturnTopic_whenFound() {
    // Given
    UUID id = UUID.randomUUID();
    PromotionTopic topic = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    PromotionTopicResponse response =
        new PromotionTopicResponse(id, 0L, "Grundlagen", null, 0, null, null, null);
    when(repository.findById(id)).thenReturn(Optional.of(topic));
    when(mapper.toResponse(topic)).thenReturn(response);

    // When
    PromotionTopicResponse result = service.get(id);

    // Then
    assertEquals("Grundlagen", result.name());
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
  void create_shouldSaveAndReturnTopic() {
    // Given
    PromotionTopicCreateRequest request = new PromotionTopicCreateRequest("Grundlagen", null, 0);
    PromotionTopic entity = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    PromotionTopicResponse response =
        new PromotionTopicResponse(UUID.randomUUID(), 0L, "Grundlagen", null, 0, null, null, null);
    Squadron squadron = new Squadron();
    squadron.setId(UUID.randomUUID());
    squadron.setShorthand("IRI");
    when(ownerScopeService.currentSquadron()).thenReturn(Optional.of(squadron));
    when(mapper.toEntity(request)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(response);

    // When
    PromotionTopicResponse result = service.create(request);

    // Then
    assertEquals("Grundlagen", result.name());
    verify(repository).save(entity);
  }

  @Test
  void update_shouldThrow_whenVersionMismatch() {
    // Given
    UUID id = UUID.randomUUID();
    PromotionTopic entity = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    entity.setVersion(1L);
    PromotionTopicUpdateRequest request =
        new PromotionTopicUpdateRequest(0L, "Grundlagen neu", null, 1);
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When / Then
    assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.update(id, request));
  }

  @Test
  void update_shouldUpdateAndReturn_whenVersionMatches() {
    // Given
    UUID id = UUID.randomUUID();
    PromotionTopic entity = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    entity.setVersion(0L);
    PromotionTopicUpdateRequest request =
        new PromotionTopicUpdateRequest(0L, "Grundlagen neu", null, 1);
    PromotionTopicResponse response =
        new PromotionTopicResponse(id, 1L, "Grundlagen neu", null, 1, null, null, null);
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(response);

    // When
    PromotionTopicResponse result = service.update(id, request);

    // Then
    assertEquals("Grundlagen neu", result.name());
    verify(mapper).updateEntity(entity, request);
  }

  @Test
  void delete_shouldCallRepositoryDelete() {
    // Given
    UUID id = UUID.randomUUID();
    PromotionTopic entity = PromotionTopic.builder().name("Grundlagen").sortOrder(0).build();
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    // When
    service.delete(id);

    // Then
    verify(repository).delete(entity);
  }
}
