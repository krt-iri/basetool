package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.mapper.PersonalBlueprintMapper;
import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintBatchResult;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Unit tests for {@link PersonalBlueprintService}. */
@ExtendWith(MockitoExtension.class)
class PersonalBlueprintServiceTest {

  private static final String SUB = "owner-1";

  @Mock private PersonalBlueprintRepository repository;
  @Mock private PersonalBlueprintMapper mapper;
  @Mock private BlueprintProductService blueprintProductService;
  @Mock private GameItemRepository gameItemRepository;

  private PersonalBlueprintService service;

  @BeforeEach
  void setUp() {
    service =
        new PersonalBlueprintService(
            repository, mapper, blueprintProductService, gameItemRepository);
  }

  private static PersonalBlueprintResponse sampleResponse() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new PersonalBlueprintResponse(
        UUID.randomUUID(), "k", "Name", null, null, null, 0L, now, now);
  }

  @Test
  void listOwn_blankQuery_usesUnfilteredOwnerLookup() {
    PersonalBlueprint entity = PersonalBlueprint.builder().productKey("k").build();
    when(repository.findAllByOwnerSub(eq(SUB), any())).thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.toResponse(entity)).thenReturn(sampleResponse());

    var page = service.listOwn(SUB, "  ", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    verify(repository).findAllByOwnerSub(eq(SUB), any());
  }

  @Test
  void listOwn_withQuery_usesNameContainsFilter() {
    when(repository.findAllByOwnerSubAndProductNameContainingIgnoreCase(eq(SUB), eq("arc"), any()))
        .thenReturn(new PageImpl<>(List.of()));

    service.listOwn(SUB, " arc ", PageRequest.of(0, 10));

    verify(repository)
        .findAllByOwnerSubAndProductNameContainingIgnoreCase(eq(SUB), eq("arc"), any());
  }

  @Test
  void add_stampsResolvedProductAndSaves_whenNotOwned() {
    when(blueprintProductService.resolveByProductKey("k"))
        .thenReturn(Optional.of(new ResolvedProduct("k", "Arclight Pistol", null)));
    when(repository.existsByOwnerSubAndProductKey(SUB, "k")).thenReturn(false);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(sampleResponse());

    Instant acquired = Instant.parse("2026-02-03T00:00:00Z");
    service.add(SUB, new PersonalBlueprintCreateRequest("k", acquired, "note"));

    ArgumentCaptor<PersonalBlueprint> captor = ArgumentCaptor.forClass(PersonalBlueprint.class);
    verify(repository).save(captor.capture());
    PersonalBlueprint saved = captor.getValue();
    assertEquals(SUB, saved.getOwnerSub());
    assertEquals("k", saved.getProductKey());
    assertEquals("Arclight Pistol", saved.getProductName());
    assertEquals(acquired, saved.getAcquiredAt());
    assertEquals("note", saved.getNote());
  }

  @Test
  void add_attachesOutputItemReference_whenProductResolvesOne() {
    UUID itemId = UUID.randomUUID();
    GameItem ref = new GameItem();
    when(blueprintProductService.resolveByProductKey("k"))
        .thenReturn(Optional.of(new ResolvedProduct("k", "Name", itemId)));
    when(repository.existsByOwnerSubAndProductKey(SUB, "k")).thenReturn(false);
    when(gameItemRepository.getReferenceById(itemId)).thenReturn(ref);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toResponse(any())).thenReturn(sampleResponse());

    service.add(SUB, new PersonalBlueprintCreateRequest("k", null, null));

    ArgumentCaptor<PersonalBlueprint> captor = ArgumentCaptor.forClass(PersonalBlueprint.class);
    verify(repository).save(captor.capture());
    assertSame(ref, captor.getValue().getOutputItem());
  }

  @Test
  void add_throwsDuplicate_whenAlreadyOwned() {
    when(blueprintProductService.resolveByProductKey("k"))
        .thenReturn(Optional.of(new ResolvedProduct("k", "Name", null)));
    when(repository.existsByOwnerSubAndProductKey(SUB, "k")).thenReturn(true);

    assertThrows(
        DuplicateEntityException.class,
        () -> service.add(SUB, new PersonalBlueprintCreateRequest("k", null, null)));
    verify(repository, never()).save(any());
  }

  @Test
  void add_throwsNotFound_whenProductKeyUnresolved() {
    when(blueprintProductService.resolveByProductKey("ghost")).thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () -> service.add(SUB, new PersonalBlueprintCreateRequest("ghost", null, null)));
  }

  @Test
  void addBatch_countsAddedAlreadyOwnedAndUnresolved() {
    when(blueprintProductService.resolveByProductKey("a"))
        .thenReturn(Optional.of(new ResolvedProduct("a", "A", null)));
    when(blueprintProductService.resolveByProductKey("a-dup"))
        .thenReturn(Optional.of(new ResolvedProduct("a", "A", null)));
    when(blueprintProductService.resolveByProductKey("b"))
        .thenReturn(Optional.of(new ResolvedProduct("b", "B", null)));
    when(blueprintProductService.resolveByProductKey("z")).thenReturn(Optional.empty());
    when(repository.existsByOwnerSubAndProductKey(SUB, "a")).thenReturn(false);
    when(repository.existsByOwnerSubAndProductKey(SUB, "b")).thenReturn(true);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PersonalBlueprintBatchResult result =
        service.addBatch(SUB, Arrays.asList("a", "b", "a-dup", "z", null));

    assertEquals(1, result.added());
    assertEquals(2, result.skippedAlreadyOwned());
    assertEquals(2, result.skippedUnresolved());
    verify(repository, org.mockito.Mockito.times(1)).save(any());
  }

  @Test
  void update_appliesChanges_whenVersionMatches() {
    UUID id = UUID.randomUUID();
    PersonalBlueprint entity =
        PersonalBlueprint.builder().id(id).ownerSub(SUB).productKey("k").productName("N").build();
    entity.setVersion(3L);
    when(repository.findByIdAndOwnerSub(id, SUB)).thenReturn(Optional.of(entity));
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(sampleResponse());

    Instant acquired = Instant.parse("2026-03-04T00:00:00Z");
    service.update(SUB, id, new PersonalBlueprintUpdateRequest(acquired, "edited", 3L));

    assertEquals(acquired, entity.getAcquiredAt());
    assertEquals("edited", entity.getNote());
    verify(repository).save(entity);
  }

  @Test
  void update_throwsOptimisticLock_whenVersionStale() {
    UUID id = UUID.randomUUID();
    PersonalBlueprint entity = PersonalBlueprint.builder().id(id).ownerSub(SUB).build();
    entity.setVersion(3L);
    when(repository.findByIdAndOwnerSub(id, SUB)).thenReturn(Optional.of(entity));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> service.update(SUB, id, new PersonalBlueprintUpdateRequest(null, null, 2L)));
    verify(repository, never()).save(any());
  }

  @Test
  void update_throwsNotFound_whenMissingOrForeign() {
    UUID id = UUID.randomUUID();
    when(repository.findByIdAndOwnerSub(id, SUB)).thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () -> service.update(SUB, id, new PersonalBlueprintUpdateRequest(null, null, 1L)));
  }

  @Test
  void delete_removesOwnedEntity() {
    UUID id = UUID.randomUUID();
    PersonalBlueprint entity = PersonalBlueprint.builder().id(id).ownerSub(SUB).build();
    when(repository.findByIdAndOwnerSub(id, SUB)).thenReturn(Optional.of(entity));

    service.delete(SUB, id);

    verify(repository).delete(entity);
  }

  @Test
  void delete_throwsNotFound_whenMissingOrForeign() {
    UUID id = UUID.randomUUID();
    when(repository.findByIdAndOwnerSub(id, SUB)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.delete(SUB, id));
  }

  // --------------------------------------------------------------- admin variants --

  @Test
  void listForUser_delegatesToOwnerLookupWithTargetSub() {
    when(repository.findAllByOwnerSub(eq("target"), any())).thenReturn(new PageImpl<>(List.of()));

    service.listForUser("target", null, PageRequest.of(0, 10));

    verify(repository).findAllByOwnerSub(eq("target"), any());
  }

  @Test
  void updateForUser_appliesByIdAlone_whenVersionMatches() {
    UUID id = UUID.randomUUID();
    PersonalBlueprint entity =
        PersonalBlueprint.builder().id(id).ownerSub("other-user").productKey("k").build();
    entity.setVersion(5L);
    when(repository.findById(id)).thenReturn(Optional.of(entity));
    when(repository.save(entity)).thenReturn(entity);
    when(mapper.toResponse(entity)).thenReturn(sampleResponse());

    service.updateForUser(id, new PersonalBlueprintUpdateRequest(null, "edited", 5L));

    assertEquals("edited", entity.getNote());
    verify(repository).save(entity);
  }

  @Test
  void updateForUser_throwsNotFound_whenIdUnknown() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () -> service.updateForUser(id, new PersonalBlueprintUpdateRequest(null, null, 1L)));
  }

  @Test
  void deleteForUser_removesById() {
    UUID id = UUID.randomUUID();
    PersonalBlueprint entity = PersonalBlueprint.builder().id(id).ownerSub("other-user").build();
    when(repository.findById(id)).thenReturn(Optional.of(entity));

    service.deleteForUser(id);

    verify(repository).delete(entity);
  }

  @Test
  void deleteForUser_throwsNotFound_whenIdUnknown() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.deleteForUser(id));
  }
}
