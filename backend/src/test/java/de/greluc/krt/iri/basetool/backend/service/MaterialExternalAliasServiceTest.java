package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.MaterialExternalAliasRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link MaterialExternalAliasService}.
 *
 * <p>Coverage for the four behaviours that matter for the R3 sync's correctness: lookup chain uses
 * the case-insensitive alias resolver, create / update reject duplicates, create / update reject a
 * missing material, and update enforces the optimistic-lock version.
 */
@ExtendWith(MockitoExtension.class)
class MaterialExternalAliasServiceTest {

  @Mock private MaterialExternalAliasRepository repository;
  @Mock private MaterialRepository materialRepository;
  @Mock private AuthHelperService authHelperService;

  @InjectMocks private MaterialExternalAliasService service;

  private Material silicon;
  private UUID siliconId;
  private UUID aliasId;

  @BeforeEach
  void setUp() {
    siliconId = UUID.randomUUID();
    silicon = new Material();
    silicon.setId(siliconId);
    silicon.setName("Silicon (Raw)");
    aliasId = UUID.randomUUID();
  }

  @Test
  void findAll_delegatesToRepositoryOrderedByExternalName() {
    MaterialExternalAlias alias = newAlias("Raw Silicon");
    when(repository.findAllByOrderByExternalNameAsc()).thenReturn(List.of(alias));

    List<MaterialExternalAlias> result = service.findAll();

    assertEquals(1, result.size());
    assertSame(alias, result.get(0));
  }

  @Test
  void findById_throwsNotFound_whenMissing() {
    when(repository.findById(aliasId)).thenReturn(Optional.empty());

    NotFoundException ex = assertThrows(NotFoundException.class, () -> service.findById(aliasId));

    assertTrue(ex.getMessage().contains(aliasId.toString()));
  }

  @Test
  void resolveMaterialByAlias_returnsLinkedMaterial_onCaseInsensitiveMatch() {
    MaterialExternalAlias alias = newAlias("Raw Silicon");
    when(repository.findBySourceSystemAndExternalNameIgnoreCase(
            MaterialExternalAliasSource.SCWIKI, "raw silicon"))
        .thenReturn(Optional.of(alias));

    Material result =
        service.resolveMaterialByAlias(MaterialExternalAliasSource.SCWIKI, "raw silicon");

    assertSame(silicon, result);
  }

  @Test
  void resolveMaterialByAlias_returnsNull_onBlankInputWithoutRepoLookup() {
    Material result = service.resolveMaterialByAlias(MaterialExternalAliasSource.SCWIKI, "  ");

    assertNull(result);
    verifyNoInteractions(repository);
  }

  @Test
  void create_stampsCreatedByFromJwt_andPersistsRow() {
    MaterialExternalAliasCreateRequest request =
        new MaterialExternalAliasCreateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, "verification note");
    when(materialRepository.findById(siliconId)).thenReturn(Optional.of(silicon));
    when(repository.findBySourceSystemAndExternalName(
            MaterialExternalAliasSource.SCWIKI, "Raw Silicon"))
        .thenReturn(Optional.empty());
    Authentication auth = new UsernamePasswordAuthenticationToken("admin-sub", "n/a");
    when(authHelperService.currentAuthentication()).thenReturn(Optional.of(auth));
    when(repository.save(any(MaterialExternalAlias.class))).thenAnswer(inv -> inv.getArgument(0));

    MaterialExternalAlias saved = service.create(request);

    assertEquals("admin-sub", saved.getCreatedBy());
    assertSame(silicon, saved.getMaterial());
    assertEquals(MaterialExternalAliasSource.SCWIKI, saved.getSourceSystem());
    assertEquals("Raw Silicon", saved.getExternalName());
    assertEquals("verification note", saved.getNote());
  }

  @Test
  void create_defaultsCreatedByToSystem_whenNoAuthenticationPresent() {
    MaterialExternalAliasCreateRequest request =
        new MaterialExternalAliasCreateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, null);
    when(materialRepository.findById(siliconId)).thenReturn(Optional.of(silicon));
    when(repository.findBySourceSystemAndExternalName(any(), any())).thenReturn(Optional.empty());
    when(authHelperService.currentAuthentication()).thenReturn(Optional.empty());
    when(repository.save(any(MaterialExternalAlias.class))).thenAnswer(inv -> inv.getArgument(0));

    MaterialExternalAlias saved = service.create(request);

    assertEquals("system", saved.getCreatedBy());
  }

  @Test
  void create_throwsNotFound_whenMaterialMissing() {
    MaterialExternalAliasCreateRequest request =
        new MaterialExternalAliasCreateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, null);
    when(materialRepository.findById(siliconId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.create(request));
    verify(repository, never()).save(any());
  }

  @Test
  void create_throwsDuplicate_whenAliasForSameSourceAndNameExists() {
    MaterialExternalAliasCreateRequest request =
        new MaterialExternalAliasCreateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, null);
    when(materialRepository.findById(siliconId)).thenReturn(Optional.of(silicon));
    when(repository.findBySourceSystemAndExternalName(
            MaterialExternalAliasSource.SCWIKI, "Raw Silicon"))
        .thenReturn(Optional.of(newAlias("Raw Silicon")));

    assertThrows(DuplicateEntityException.class, () -> service.create(request));
    verify(repository, never()).save(any());
  }

  @Test
  void update_throwsOptimisticLock_onVersionMismatch() {
    MaterialExternalAlias existing = newAlias("Raw Silicon");
    existing.setId(aliasId);
    existing.setVersion(5L);
    when(repository.findById(aliasId)).thenReturn(Optional.of(existing));

    MaterialExternalAliasUpdateRequest request =
        new MaterialExternalAliasUpdateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, null, 4L);

    assertThrows(
        ObjectOptimisticLockingFailureException.class, () -> service.update(aliasId, request));
    verify(repository, never()).save(any());
  }

  @Test
  void update_acceptsCorrectVersion_andPersistsChanges() {
    MaterialExternalAlias existing = newAlias("Raw Silicon");
    existing.setId(aliasId);
    existing.setVersion(7L);
    when(repository.findById(aliasId)).thenReturn(Optional.of(existing));
    when(materialRepository.findById(siliconId)).thenReturn(Optional.of(silicon));
    when(repository.findBySourceSystemAndExternalName(
            MaterialExternalAliasSource.SCWIKI, "Raw Silicon"))
        .thenReturn(Optional.empty());
    when(repository.save(any(MaterialExternalAlias.class))).thenAnswer(inv -> inv.getArgument(0));

    MaterialExternalAliasUpdateRequest request =
        new MaterialExternalAliasUpdateRequest(
            siliconId, "SCWIKI", "Raw Silicon", "wiki-key", null, "AGRI", "updated note", 7L);

    MaterialExternalAlias saved = service.update(aliasId, request);

    assertEquals("wiki-key", saved.getExternalKey());
    assertEquals("AGRI", saved.getExternalCode());
    assertEquals("updated note", saved.getNote());
  }

  @Test
  void update_throwsDuplicate_whenAnotherRowAlreadyHoldsTheSourceAndName() {
    MaterialExternalAlias existing = newAlias("Old Name");
    existing.setId(aliasId);
    existing.setVersion(0L);
    when(repository.findById(aliasId)).thenReturn(Optional.of(existing));
    when(materialRepository.findById(siliconId)).thenReturn(Optional.of(silicon));

    MaterialExternalAlias other = newAlias("Raw Silicon");
    other.setId(UUID.randomUUID());
    when(repository.findBySourceSystemAndExternalName(
            MaterialExternalAliasSource.SCWIKI, "Raw Silicon"))
        .thenReturn(Optional.of(other));

    MaterialExternalAliasUpdateRequest request =
        new MaterialExternalAliasUpdateRequest(
            siliconId, "SCWIKI", "Raw Silicon", null, null, null, null, 0L);

    assertThrows(DuplicateEntityException.class, () -> service.update(aliasId, request));
    verify(repository, never()).save(any());
  }

  @Test
  void delete_removesRow_whenPresent() {
    MaterialExternalAlias alias = newAlias("Raw Silicon");
    alias.setId(aliasId);
    when(repository.findById(aliasId)).thenReturn(Optional.of(alias));

    service.delete(aliasId);

    verify(repository).delete(eq(alias));
  }

  private MaterialExternalAlias newAlias(String externalName) {
    MaterialExternalAlias alias = new MaterialExternalAlias();
    alias.setMaterial(silicon);
    alias.setSourceSystem(MaterialExternalAliasSource.SCWIKI);
    alias.setExternalName(externalName);
    return alias;
  }
}
