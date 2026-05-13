package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityDto;
import de.greluc.krt.iri.basetool.backend.dto.uex.UexCommodityPriceDto;
import de.greluc.krt.iri.basetool.backend.integration.UexClient;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialPrice;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UexCommodityServiceTest {

  @Mock private UexClient uexClient;

  @Mock private MaterialRepository materialRepository;

  @Mock private MaterialPriceRepository materialPriceRepository;

  @Mock private TerminalRepository terminalRepository;

  @InjectMocks private UexCommodityService uexCommodityService;

  @Test
  void shouldProcessCommodityDtoAndCreateNewMaterialAndLocation() {
    // Given
    UexCommodityPriceDto dto =
        UexCommodityPriceDto.builder()
            .idCommodity(1)
            .commodityName("Laranite")
            .idTerminal(10)
            .terminalName("Area18")
            .priceBuy(BigDecimal.valueOf(25.5))
            .priceSell(BigDecimal.valueOf(30.0))
            .build();

    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(dto));
    when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Laranite")).thenReturn(Optional.empty());

    Material savedMaterial = new Material();
    savedMaterial.setId(UUID.randomUUID());
    when(materialRepository.save(any(Material.class))).thenReturn(savedMaterial);

    Terminal mockTerminal = new Terminal();
    mockTerminal.setId(UUID.randomUUID());
    mockTerminal.setIdTerminal(10);
    mockTerminal.setCityName("Area18");
    when(terminalRepository.findByIdTerminal(10)).thenReturn(Optional.of(mockTerminal));

    when(materialPriceRepository.findByMaterialIdAndTerminalId(
            savedMaterial.getId(), mockTerminal.getId()))
        .thenReturn(Optional.empty());

    // When
    uexCommodityService.fetchAndProcessCommoditiesPrices();

    // Then
    ArgumentCaptor<MaterialPrice> priceCaptor = ArgumentCaptor.forClass(MaterialPrice.class);
    verify(materialPriceRepository).save(priceCaptor.capture());

    MaterialPrice savedPrice = priceCaptor.getValue();
    assertEquals(BigDecimal.valueOf(25.5), savedPrice.getPriceBuy());
    assertEquals(BigDecimal.valueOf(30.0), savedPrice.getPriceSell());
  }

  // ─── Commodity catalogue sync (first pass) ──────────────────────────────

  @Test
  void commoditySync_savesRefinedMaterialWithCorrectType() {
    // Given
    UexCommodityDto refined = commodity(1, "Titanium", /*isRefined*/ 1, /*isRefinable*/ 0);
    when(uexClient.getCommodities()).thenReturn(List.of(refined));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Titanium")).thenReturn(Optional.empty());

    // When
    uexCommodityService.fetchAndProcessCommoditiesPrices();

    // Then
    ArgumentCaptor<Material> cap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(cap.capture());
    assertEquals(1, cap.getValue().getIdCommodity());
    assertEquals("Titanium", cap.getValue().getName());
    assertEquals(MaterialType.REFINED, cap.getValue().getType());
  }

  @Test
  void commoditySync_savesRefinableMaterialAsRawType() {
    UexCommodityDto raw = commodity(2, "Quantanium", 0, 1);
    when(uexClient.getCommodities()).thenReturn(List.of(raw));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(2)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Quantanium")).thenReturn(Optional.empty());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<Material> cap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(cap.capture());
    assertEquals(MaterialType.RAW, cap.getValue().getType());
  }

  @Test
  void commoditySync_savesNonRefinableAsNoRefineType() {
    UexCommodityDto inert = commodity(3, "Stims", 0, 0);
    when(uexClient.getCommodities()).thenReturn(List.of(inert));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(3)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Stims")).thenReturn(Optional.empty());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<Material> cap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(cap.capture());
    assertEquals(MaterialType.NO_REFINE, cap.getValue().getType());
  }

  @Test
  void commoditySync_reusesExistingMaterial_whenIdCommodityMatches() {
    // Given an existing material with id_commodity=4
    UUID existingId = UUID.randomUUID();
    Material existing = new Material();
    existing.setId(existingId);
    existing.setIdCommodity(4);
    existing.setName("Old Name");
    existing.setVersion(7L);

    UexCommodityDto updated = commodity(4, "New Name", 1, 0);
    when(uexClient.getCommodities()).thenReturn(List.of(updated));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(4)).thenReturn(Optional.of(existing));

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<Material> cap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(cap.capture());
    assertSame(existing, cap.getValue(), "Existing entity must be mutated in place");
    assertEquals(existingId, cap.getValue().getId());
    assertEquals(7L, cap.getValue().getVersion());
    verify(materialRepository, never()).findByName(any());
  }

  @Test
  void commoditySync_linksByName_whenIdCommodityIsNewButNameMatches() {
    // Given an existing material with no id_commodity but matching name
    Material existingByName = new Material();
    existingByName.setName("Diamond");
    existingByName.setIdCommodity(null);

    UexCommodityDto fresh = commodity(5, "Diamond", 0, 0);
    when(uexClient.getCommodities()).thenReturn(List.of(fresh));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(5)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Diamond")).thenReturn(Optional.of(existingByName));

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<Material> cap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(cap.capture());
    assertSame(existingByName, cap.getValue());
    assertEquals(
        5, cap.getValue().getIdCommodity(), "id_commodity must be backfilled from the UEX payload");
  }

  @Test
  void commoditySync_skipsDtoWithoutIdOrName() {
    UexCommodityDto noId = commodity(null, "Has Name", 0, 0);
    UexCommodityDto noName = commodity(99, null, 0, 0);
    when(uexClient.getCommodities()).thenReturn(List.of(noId, noName));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    verify(materialRepository, never()).save(any());
  }

  @Test
  void commoditySync_swallowsExceptionPerRow_andContinuesBatch() {
    // Given — first row save throws, second must still save
    UexCommodityDto bad = commodity(10, "Bad", 0, 0);
    UexCommodityDto good = commodity(11, "Good", 0, 0);
    when(uexClient.getCommodities()).thenReturn(List.of(bad, good));
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());
    when(materialRepository.findByIdCommodity(10)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Bad")).thenReturn(Optional.empty());
    when(materialRepository.findByIdCommodity(11)).thenReturn(Optional.empty());
    when(materialRepository.findByName("Good")).thenReturn(Optional.empty());

    when(materialRepository.save(any()))
        .thenThrow(new RuntimeException("DB hiccup"))
        .thenReturn(new Material());

    // When
    assertDoesNotThrow(() -> uexCommodityService.fetchAndProcessCommoditiesPrices());

    // Then — both rows attempted
    verify(materialRepository, times(2)).save(any());
  }

  @Test
  void commoditySync_emptyResponse_stillRunsPriceSync() {
    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    verify(uexClient).getCommodities();
    verify(uexClient).getCommoditiesPricesAll();
    verify(materialRepository, never()).save(any());
  }

  // ─── Price sync (second pass) ───────────────────────────────────────────

  @Test
  void priceSync_updatesExistingPriceRow_inPlace() {
    UUID materialId = UUID.randomUUID();
    UUID terminalId = UUID.randomUUID();
    UUID priceId = UUID.randomUUID();
    Material material = new Material();
    material.setId(materialId);
    material.setIdCommodity(1);
    Terminal terminal = new Terminal();
    terminal.setId(terminalId);
    terminal.setIdTerminal(42);

    MaterialPrice existing = new MaterialPrice();
    existing.setId(priceId);
    existing.setMaterial(material);
    existing.setTerminal(terminal);
    existing.setPriceBuy(new BigDecimal("10"));
    existing.setVersion(2L);

    UexCommodityPriceDto fresh =
        new UexCommodityPriceDto(
            1,
            "Gold",
            42,
            "term",
            new BigDecimal("99.00"),
            new BigDecimal("111.00"),
            100,
            50,
            0,
            0,
            1,
            1700000100L);

    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(fresh));
    when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.of(material));
    when(terminalRepository.findByIdTerminal(42)).thenReturn(Optional.of(terminal));
    when(materialPriceRepository.findByMaterialIdAndTerminalId(materialId, terminalId))
        .thenReturn(Optional.of(existing));

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<MaterialPrice> cap = ArgumentCaptor.forClass(MaterialPrice.class);
    verify(materialPriceRepository).save(cap.capture());
    assertSame(existing, cap.getValue());
    assertEquals(priceId, cap.getValue().getId());
    assertEquals(
        2L, cap.getValue().getVersion(), "Version must remain — JPA owns optimistic locking");
    assertEquals(new BigDecimal("99.00"), cap.getValue().getPriceBuy());
    assertEquals(Boolean.FALSE, cap.getValue().getStatusBuy(), "0 maps to false");
    assertEquals(Boolean.TRUE, cap.getValue().getStatusSell(), "1 maps to true");
    assertEquals(Instant.ofEpochSecond(1700000100L), cap.getValue().getDateModified());
  }

  @Test
  void priceSync_skipsRow_whenTerminalUnknown() {
    Material material = new Material();
    material.setId(UUID.randomUUID());
    material.setIdCommodity(1);
    UexCommodityPriceDto orphan =
        new UexCommodityPriceDto(
            1, "X", 9999, "Unknown", BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, 0, 0, 1L);

    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(orphan));
    when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.of(material));
    when(terminalRepository.findByIdTerminal(9999)).thenReturn(Optional.empty());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    verify(materialPriceRepository, never()).save(any());
  }

  @Test
  void priceSync_skipsRow_whenIdCommodityOrIdTerminalMissing() {
    UexCommodityPriceDto noCommodity =
        new UexCommodityPriceDto(
            null, "X", 1, "T", BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, 0, 0, 0L);
    UexCommodityPriceDto noTerminal =
        new UexCommodityPriceDto(
            1, "X", null, "T", BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, 0, 0, 0L);
    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(noCommodity, noTerminal));

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    verify(materialPriceRepository, never()).save(any());
  }

  @Test
  void priceSync_createsPlaceholderMaterial_whenIdAndNameUnknown() {
    // Given a price row referencing a commodity we have never seen
    UUID terminalId = UUID.randomUUID();
    Terminal terminal = new Terminal();
    terminal.setId(terminalId);
    terminal.setIdTerminal(7);

    UexCommodityPriceDto payload =
        new UexCommodityPriceDto(
            123, "NewStuff", 7, "Terminal", BigDecimal.TEN, BigDecimal.TEN, 1, 1, 1, 1, 1, 1L);

    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(payload));
    when(materialRepository.findByIdCommodity(123)).thenReturn(Optional.empty());
    when(materialRepository.findByName("NewStuff")).thenReturn(Optional.empty());
    when(terminalRepository.findByIdTerminal(7)).thenReturn(Optional.of(terminal));
    when(materialRepository.save(any(Material.class)))
        .thenAnswer(
            invocation -> {
              Material m = invocation.getArgument(0);
              if (m.getId() == null) {
                m.setId(UUID.randomUUID());
              }
              return m;
            });
    when(materialPriceRepository.findByMaterialIdAndTerminalId(any(), any()))
        .thenReturn(Optional.empty());

    uexCommodityService.fetchAndProcessCommoditiesPrices();

    ArgumentCaptor<Material> matCap = ArgumentCaptor.forClass(Material.class);
    verify(materialRepository).save(matCap.capture());
    assertEquals(123, matCap.getValue().getIdCommodity());
    assertEquals("NewStuff", matCap.getValue().getName());
    assertEquals(
        MaterialType.NO_REFINE,
        matCap.getValue().getType(),
        "Placeholder materials default to NO_REFINE");

    verify(materialPriceRepository).save(any(MaterialPrice.class));
  }

  @Test
  void priceSync_swallowsExceptionPerRow_andContinuesBatch() {
    UexCommodityPriceDto bad =
        new UexCommodityPriceDto(
            1, "A", 1, "T1", BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, 0, 0, 1L);
    UexCommodityPriceDto good =
        new UexCommodityPriceDto(
            2, "B", 2, "T2", BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, 0, 0, 1L);

    Material m1 = new Material();
    m1.setId(UUID.randomUUID());
    m1.setIdCommodity(1);
    Material m2 = new Material();
    m2.setId(UUID.randomUUID());
    m2.setIdCommodity(2);
    Terminal t1 = new Terminal();
    t1.setId(UUID.randomUUID());
    t1.setIdTerminal(1);
    Terminal t2 = new Terminal();
    t2.setId(UUID.randomUUID());
    t2.setIdTerminal(2);

    when(uexClient.getCommodities()).thenReturn(List.of());
    when(uexClient.getCommoditiesPricesAll()).thenReturn(List.of(bad, good));
    when(materialRepository.findByIdCommodity(1)).thenReturn(Optional.of(m1));
    when(materialRepository.findByIdCommodity(2)).thenReturn(Optional.of(m2));
    when(terminalRepository.findByIdTerminal(1)).thenReturn(Optional.of(t1));
    when(terminalRepository.findByIdTerminal(2)).thenReturn(Optional.of(t2));
    when(materialPriceRepository.findByMaterialIdAndTerminalId(any(), any()))
        .thenReturn(Optional.empty());

    when(materialPriceRepository.save(any()))
        .thenThrow(new RuntimeException("first row hiccup"))
        .thenReturn(new MaterialPrice());

    assertDoesNotThrow(() -> uexCommodityService.fetchAndProcessCommoditiesPrices());

    verify(materialPriceRepository, times(2)).save(any());
  }

  // ─── helper ─────────────────────────────────────────────────────────────

  private static UexCommodityDto commodity(
      Integer id, String name, Integer isRefined, Integer isRefinable) {
    return new UexCommodityDto(
        id,
        name,
        /*code*/ "C", /*slug*/
        "s", /*kind*/
        "k", /*type*/
        "t",
        /*weightScu*/ 1.0, /*priceBuy*/
        1.0, /*priceSell*/
        1.0,
        /*isAvailable*/ 1, /*isAvailableLive*/
        1, /*isExtractable*/
        0,
        /*isMineral*/ 0, /*isRaw*/
        0, /*isPure*/
        0,
        isRefinable,
        isRefined,
        /*isHarvestable*/ 0, /*isBuyable*/
        1, /*isSellable*/
        1, /*isTemporary*/
        0,
        /*isIllegal*/ 0, /*isVolatileQt*/
        0, /*isVolatileTime*/
        0,
        /*isInert*/ 0, /*isExplosive*/
        0, /*isBuggy*/
        0, /*isFuel*/
        0);
  }
}
