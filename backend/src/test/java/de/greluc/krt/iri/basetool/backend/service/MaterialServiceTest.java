package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.backend.repository.MaterialPriceRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

  @Mock private MaterialRepository materialRepository;

  @Mock private MaterialPriceRepository materialPriceRepository;

  @InjectMocks private MaterialService materialService;

  @Test
  void getMaterialPriceOverview_ShouldReturnPageOfOverviews() {
    // Arrange
    String nameFilter = "Gold";
    PageRequest pageRequest = PageRequest.of(0, 10);
    MaterialPriceOverviewDto dto =
        new MaterialPriceOverviewDto(
            UUID.randomUUID(),
            "Gold",
            new de.greluc.krt.iri.basetool.backend.model.dto.MaterialCategoryDto(
                UUID.randomUUID(), "Mineral", 0L),
            false,
            false,
            false,
            new BigDecimal("5.0"),
            new BigDecimal("7.0"));
    Page<MaterialPriceOverviewDto> expectedPage = new PageImpl<>(List.of(dto));

    when(materialRepository.getMaterialPriceOverview(nameFilter, pageRequest))
        .thenReturn(expectedPage);

    // Act
    Page<MaterialPriceOverviewDto> result =
        materialService.getMaterialPriceOverview(nameFilter, pageRequest);

    // Assert
    assertEquals(1, result.getTotalElements());
    assertEquals("Gold", result.getContent().get(0).name());
    assertEquals(new BigDecimal("5.0"), result.getContent().get(0).minPriceBuy());
  }

  @Test
  void getMaterialPrices_ShouldReturnPageOfPrices() {
    // Arrange
    UUID materialId = UUID.randomUUID();
    PageRequest pageRequest = PageRequest.of(0, 10);
    MaterialPriceDto dto =
        new MaterialPriceDto(
            UUID.randomUUID(),
            "Area18",
            new BigDecimal("5.0"),
            new BigDecimal("7.0"),
            100,
            200,
            true,
            true);
    Page<MaterialPriceDto> expectedPage = new PageImpl<>(List.of(dto));

    when(materialPriceRepository.findPricesByMaterialId(materialId, pageRequest))
        .thenReturn(expectedPage);

    // Act
    Page<MaterialPriceDto> result = materialService.getMaterialPrices(materialId, pageRequest);

    // Assert
    assertEquals(1, result.getTotalElements());
    assertEquals("Area18", result.getContent().get(0).terminalName());
    assertEquals(new BigDecimal("5.0"), result.getContent().get(0).priceBuy());
  }

  @Test
  void getAllJobOrderMaterials_ShouldReturnOnlyJobOrderMaterials() {
    // Given
    Material mat1 = new Material();
    mat1.setId(UUID.randomUUID());
    mat1.setName("Agricium");
    mat1.setIsJobOrder(true);

    Material mat2 = new Material();
    mat2.setId(UUID.randomUUID());
    mat2.setName("Bexalite");
    mat2.setIsJobOrder(true);

    when(materialRepository.findAllByIsJobOrderTrueOrderByNameAsc())
        .thenReturn(List.of(mat1, mat2));

    // When
    List<Material> result = materialService.getAllJobOrderMaterials();

    // Then
    assertEquals(2, result.size());
    assertEquals("Agricium", result.get(0).getName());
    assertEquals("Bexalite", result.get(1).getName());
    verify(materialRepository).findAllByIsJobOrderTrueOrderByNameAsc();
  }
}
