package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.BlueprintExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportPreviewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResolutionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportResultDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportStatus;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintExternalAliasRepository;
import de.greluc.krt.iri.basetool.backend.repository.GameItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link BlueprintImportService}. */
@ExtendWith(MockitoExtension.class)
class BlueprintImportServiceTest {

  private static final String SUB = "owner-1";
  private static final BlueprintExternalAliasSource SCMDB = BlueprintExternalAliasSource.SCMDB;

  @Mock private BlueprintProductService blueprintProductService;
  @Mock private BlueprintExternalAliasRepository aliasRepository;
  @Mock private PersonalBlueprintRepository personalBlueprintRepository;
  @Mock private GameItemRepository gameItemRepository;

  private BlueprintImportService service;

  @BeforeEach
  void setUp() {
    service =
        new BlueprintImportService(
            JsonMapper.builder().build(),
            blueprintProductService,
            new BlueprintNameNormalizer(),
            new BlueprintFuzzyMatcher(),
            aliasRepository,
            personalBlueprintRepository,
            gameItemRepository);
  }

  private static ResolvedProduct product(String key, String name) {
    return new ResolvedProduct(key, name, null);
  }

  private static MultipartFile upload(String json) {
    return new MockMultipartFile("file", "scmdb.json", "application/json", json.getBytes());
  }

  // ---------------------------------------------------------------- preview --

  @Test
  void preview_exactNameMatches() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));

    BlueprintImportPreviewDto preview =
        service.previewImport(
            SUB, upload("{\"blueprints\":[{\"productName\":\"Arclight Pistol\"}]}"));

    assertEquals(1, preview.total());
    assertEquals(1, preview.matched());
    BlueprintImportEntryDto entry = preview.entries().get(0);
    assertEquals(BlueprintImportStatus.MATCHED, entry.status());
    assertEquals("arclight pistol", entry.productKey());
  }

  @Test
  void preview_aliasMatches_whenNoExactButAliasExists() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));
    BlueprintExternalAlias alias = new BlueprintExternalAlias();
    alias.setSourceSystem(SCMDB);
    alias.setExternalName("Arc-Light Pistol");
    alias.setProductKey("arclight pistol");
    alias.setProductName("Arclight Pistol");
    when(aliasRepository.findBySourceSystemAndExternalNameIgnoreCase(SCMDB, "Arc-Light Pistol"))
        .thenReturn(Optional.of(alias));

    BlueprintImportPreviewDto preview =
        service.previewImport(
            SUB, upload("{\"blueprints\":[{\"productName\":\"Arc-Light Pistol\"}]}"));

    assertEquals(1, preview.matchedByAlias());
    BlueprintImportEntryDto entry = preview.entries().get(0);
    assertEquals(BlueprintImportStatus.MATCHED_BY_ALIAS, entry.status());
    assertEquals("arclight pistol", entry.productKey());
  }

  @Test
  void preview_fuzzySuggestsForCloseTypo() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("calico legs tactical", "Calico Legs Tactical")));

    BlueprintImportPreviewDto preview =
        service.previewImport(
            SUB, upload("{\"blueprints\":[{\"productName\":\"Calico Legs Tacticl\"}]}"));

    assertEquals(1, preview.suggested());
    BlueprintImportEntryDto entry = preview.entries().get(0);
    assertEquals(BlueprintImportStatus.SUGGESTED, entry.status());
    assertNull(entry.productKey());
    assertEquals("calico legs tactical", entry.suggestions().get(0).productKey());
  }

  @Test
  void preview_unmatchedWhenNothingClose() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));

    BlueprintImportPreviewDto preview =
        service.previewImport(
            SUB, upload("{\"blueprints\":[{\"productName\":\"Quantum Drive XL\"}]}"));

    assertEquals(1, preview.unmatched());
    assertEquals(BlueprintImportStatus.UNMATCHED, preview.entries().get(0).status());
  }

  @Test
  void preview_alreadyOwnedOverridesMatched() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));
    PersonalBlueprint owned =
        PersonalBlueprint.builder().ownerSub(SUB).productKey("arclight pistol").build();
    when(personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(eq(SUB), any()))
        .thenReturn(List.of(owned));

    BlueprintImportPreviewDto preview =
        service.previewImport(
            SUB, upload("{\"blueprints\":[{\"productName\":\"Arclight Pistol\"}]}"));

    assertEquals(0, preview.matched());
    assertEquals(1, preview.alreadyOwned());
    assertEquals(BlueprintImportStatus.ALREADY_OWNED, preview.entries().get(0).status());
  }

  @Test
  void preview_dedupesByNameAndKeepsEarliestTimestamp() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));

    // Two records of the same product; the later ts appears first in the file.
    String json =
        "{\"blueprints\":["
            + "{\"productName\":\"Arclight Pistol\",\"ts\":1774534484.0},"
            + "{\"productName\":\"Arclight Pistol\",\"ts\":1700000000.0}]}";
    BlueprintImportPreviewDto preview = service.previewImport(SUB, upload(json));

    assertEquals(1, preview.total());
    assertEquals(
        Instant.ofEpochMilli(1700000000000L), preview.entries().get(0).suggestedAcquiredAt());
  }

  @Test
  void preview_acceptsBareArrayForm() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));

    BlueprintImportPreviewDto preview =
        service.previewImport(SUB, upload("[{\"productName\":\"Arclight Pistol\"}]"));

    assertEquals(1, preview.matched());
  }

  @Test
  void preview_emptyFileThrowsBadRequest() {
    MultipartFile empty =
        new MockMultipartFile("file", "scmdb.json", "application/json", new byte[0]);
    assertThrows(BadRequestException.class, () -> service.previewImport(SUB, empty));
  }

  @Test
  void preview_malformedJsonThrowsBadRequest() {
    MultipartFile bad = upload("{not-json");
    assertThrows(BadRequestException.class, () -> service.previewImport(SUB, bad));
  }

  @Test
  void preview_missingBlueprintsArrayThrowsBadRequest() {
    MultipartFile wrong = upload("{\"foo\":1}");
    assertThrows(BadRequestException.class, () -> service.previewImport(SUB, wrong));
  }

  // ------------------------------------------------------------------ apply --

  @Test
  void apply_addsRowAndLearnsAliasForManualPick() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("calico legs tactical", "Calico Legs Tactical")));
    when(personalBlueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // External name differs from the chosen key by normalization -> a manual resolution.
    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto(
                    "Calico Legs (Tactical)", "calico legs tactical", null, "from import")));

    assertEquals(1, result.added());
    assertEquals(1, result.aliasesLearned());
    assertEquals(0, result.skipped());
    verify(personalBlueprintRepository, times(1)).save(any());
    verify(aliasRepository, times(1)).save(any(BlueprintExternalAlias.class));
  }

  @Test
  void apply_doesNotLearnAliasWhenNameAlreadyNormalizesToKey() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("calico legs tactical", "Calico Legs Tactical")));
    when(personalBlueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto(
                    "Calico Legs Tactical", "calico legs tactical", null, null)));

    assertEquals(1, result.added());
    assertEquals(0, result.aliasesLearned());
    verify(aliasRepository, never()).save(any());
  }

  @Test
  void apply_skipsBlankAndUnresolvableKeys() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("arclight pistol", "Arclight Pistol")));

    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto("Skipped One", "  ", null, null),
                new BlueprintImportResolutionDto("Ghost", "no-such-key", null, null)));

    assertEquals(0, result.added());
    assertEquals(2, result.skipped());
    verify(personalBlueprintRepository, never()).save(any());
  }

  @Test
  void apply_countsAlreadyOwnedAndDedupesWithinRequest() {
    when(blueprintProductService.allProducts())
        .thenReturn(
            List.of(
                product("arclight pistol", "Arclight Pistol"),
                product("calico legs", "Calico Legs")));
    PersonalBlueprint owned =
        PersonalBlueprint.builder().ownerSub(SUB).productKey("arclight pistol").build();
    when(personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(eq(SUB), any()))
        .thenReturn(List.of(owned));
    when(personalBlueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto("Arclight Pistol", "arclight pistol", null, null),
                new BlueprintImportResolutionDto("Calico Legs", "calico legs", null, null),
                new BlueprintImportResolutionDto("Calico Legs Again", "calico legs", null, null)));

    assertEquals(1, result.added());
    assertEquals(2, result.alreadyOwned());
    verify(personalBlueprintRepository, times(1)).save(any());
  }

  @Test
  void apply_isBulkSafe_savesEachNewRowExactlyOnce() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("a", "A"), product("b", "B"), product("c", "C")));
    when(personalBlueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto("A", "a", null, null),
                new BlueprintImportResolutionDto("B", "b", null, null),
                new BlueprintImportResolutionDto("C", "c", null, null)));

    assertEquals(3, result.added());
    // No double save (no @Version re-bump from a detaching bulk update mid-loop).
    verify(personalBlueprintRepository, times(3)).save(any());
  }

  @Test
  void apply_doesNotDuplicateAliasWhenOneAlreadyExists() {
    when(blueprintProductService.allProducts())
        .thenReturn(List.of(product("calico legs tactical", "Calico Legs Tactical")));
    when(personalBlueprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(aliasRepository.findBySourceSystemAndExternalName(SCMDB, "Calico Legs (Tactical)"))
        .thenReturn(Optional.of(new BlueprintExternalAlias()));

    BlueprintImportResultDto result =
        service.applyImport(
            SUB,
            List.of(
                new BlueprintImportResolutionDto(
                    "Calico Legs (Tactical)", "calico legs tactical", null, null)));

    assertEquals(1, result.added());
    assertEquals(0, result.aliasesLearned());
    verify(aliasRepository, never()).save(any());
  }
}
