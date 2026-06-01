package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintProductDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintProductRow;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BlueprintProductService}. */
@ExtendWith(MockitoExtension.class)
class BlueprintProductServiceTest {

  private static final String SUB = "owner-1";

  @Mock private BlueprintRepository blueprintRepository;
  @Mock private PersonalBlueprintRepository personalBlueprintRepository;

  private BlueprintProductService service;

  @BeforeEach
  void setUp() {
    service =
        new BlueprintProductService(
            blueprintRepository, personalBlueprintRepository, new BlueprintNameNormalizer());
  }

  private static BlueprintProductRow row(
      String outputName, String key, String manufacturer, UUID outputItemId) {
    return new BlueprintProductRow(outputName, key, manufacturer, outputItemId);
  }

  private void noneOwned() {
    when(personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(
            eq(SUB), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
  }

  @Test
  void searchProducts_dedupesMultiVariantNamesAndCountsThem() {
    UUID itemId = UUID.randomUUID();
    when(blueprintRepository.findActiveProductRows(""))
        .thenReturn(
            List.of(
                row("Arclight Pistol", "BP_A", "Behring", itemId),
                row("Arclight Pistol", "BP_B", null, null)));
    noneOwned();

    List<BlueprintProductDto> result = service.searchProducts(null, 25, SUB);

    assertEquals(1, result.size());
    BlueprintProductDto dto = result.get(0);
    assertEquals("arclight pistol", dto.productKey());
    assertEquals("Arclight Pistol", dto.name());
    assertEquals(2, dto.variantCount());
    assertEquals("BP_A", dto.exampleKey());
    assertEquals("Behring", dto.manufacturerName());
    assertFalse(dto.ownedByCurrentUser());
  }

  @Test
  void searchProducts_passesTrimmedQueryToRepository() {
    when(blueprintRepository.findActiveProductRows("pistol")).thenReturn(List.of());

    service.searchProducts("  pistol  ", 25, SUB);

    verify(blueprintRepository).findActiveProductRows("pistol");
  }

  @Test
  void searchProducts_sortsAlphabeticallyAndCapsToLimit() {
    when(blueprintRepository.findActiveProductRows(""))
        .thenReturn(
            List.of(
                row("Bravo", "B", null, null),
                row("Alpha", "A", null, null),
                row("Charlie", "C", null, null)));
    noneOwned();

    List<BlueprintProductDto> result = service.searchProducts("", 2, SUB);

    assertEquals(2, result.size());
    assertEquals("Alpha", result.get(0).name());
    assertEquals("Bravo", result.get(1).name());
  }

  @Test
  void searchProducts_clampsNonPositiveLimitToOne() {
    when(blueprintRepository.findActiveProductRows(""))
        .thenReturn(List.of(row("Alpha", "A", null, null), row("Bravo", "B", null, null)));
    noneOwned();

    List<BlueprintProductDto> result = service.searchProducts("", 0, SUB);

    assertEquals(1, result.size());
  }

  @Test
  void searchProducts_marksProductsOwnedByCaller() {
    when(blueprintRepository.findActiveProductRows(""))
        .thenReturn(List.of(row("Alpha", "A", null, null), row("Bravo", "B", null, null)));
    when(personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(
            eq(SUB), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(PersonalBlueprint.builder().productKey("alpha").build()));

    List<BlueprintProductDto> result = service.searchProducts("", 25, SUB);

    BlueprintProductDto alpha =
        result.stream().filter(d -> d.name().equals("Alpha")).findFirst().orElseThrow();
    BlueprintProductDto bravo =
        result.stream().filter(d -> d.name().equals("Bravo")).findFirst().orElseThrow();
    assertTrue(alpha.ownedByCurrentUser());
    assertFalse(bravo.ownedByCurrentUser());
  }

  @Test
  void resolveByProductKey_returnsCanonicalProductWithOutputItemId() {
    UUID itemId = UUID.randomUUID();
    when(blueprintRepository.findActiveProductRows(""))
        .thenReturn(List.of(row("Arclight Pistol", "BP_A", "Behring", itemId)));

    Optional<ResolvedProduct> resolved = service.resolveByProductKey("arclight pistol");

    assertTrue(resolved.isPresent());
    assertEquals("arclight pistol", resolved.get().productKey());
    assertEquals("Arclight Pistol", resolved.get().productName());
    assertEquals(itemId, resolved.get().outputItemId());
  }

  @Test
  void resolveByProductKey_returnsEmptyForBlankKey() {
    assertTrue(service.resolveByProductKey("  ").isEmpty());
    verify(blueprintRepository, org.mockito.Mockito.never()).findActiveProductRows(anyString());
  }

  @Test
  void resolveByProductKey_returnsEmptyForUnknownKey() {
    when(blueprintRepository.findActiveProductRows("")).thenReturn(List.of());

    assertTrue(service.resolveByProductKey("does-not-exist").isEmpty());
  }
}
