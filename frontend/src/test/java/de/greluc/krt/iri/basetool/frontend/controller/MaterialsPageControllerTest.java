package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@SuppressWarnings("unchecked")
class MaterialsPageControllerTest {

  @Test
  void listMaterials_ShouldAddMaterialsToModel() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MaterialsPageController controller = new MaterialsPageController(backendApiClient);
    Model model = new ConcurrentModel();

    MaterialPriceOverviewDto dto =
        new MaterialPriceOverviewDto(
            UUID.randomUUID(),
            "Gold",
            new de.greluc.krt.iri.basetool.frontend.model.dto.MaterialCategoryDto(
                UUID.randomUUID(), "Mineral", 0L),
            false,
            false,
            false,
            new BigDecimal("5.0"),
            new BigDecimal("7.0"));
    PageResponse<MaterialPriceOverviewDto> pageResponse =
        new PageResponse<>(List.of(dto), 0, 10000, 1, 1, Collections.emptyList());

    when(backendApiClient.get(
            eq("/api/v1/materials/prices-overview?size=10000&sort=name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(pageResponse);

    // Act
    String viewName = controller.listMaterials(model);

    // Assert
    assertEquals("materials", viewName);
    List<MaterialPriceOverviewDto> materials =
        (List<MaterialPriceOverviewDto>) model.getAttribute("materials");
    assertNotNull(materials);
    assertEquals(1, materials.size());
    assertEquals("Gold", materials.get(0).name());
  }

  @Test
  void listMaterials_ShouldHandleErrorAndAddEmptyListToModel() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MaterialsPageController controller = new MaterialsPageController(backendApiClient);
    Model model = new ConcurrentModel();

    when(backendApiClient.get(
            eq("/api/v1/materials/prices-overview?size=10000&sort=name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("API Error"));

    // Act
    String viewName = controller.listMaterials(model);

    // Assert
    assertEquals("materials", viewName);
    List<MaterialPriceOverviewDto> materials =
        (List<MaterialPriceOverviewDto>) model.getAttribute("materials");
    assertNotNull(materials);
    assertTrue(materials.isEmpty());
    assertEquals("error.materials.load", model.getAttribute("error"));
  }

  @Test
  void getMaterialDetail_ShouldAddMaterialAndPricesToModel() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MaterialsPageController controller = new MaterialsPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID id = UUID.randomUUID();

    MaterialDto materialDto =
        new MaterialDto(
            id,
            1,
            "Gold",
            "Metal",
            "SCU",
            "Test description",
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            0L);
    when(backendApiClient.get(eq("/api/v1/materials/" + id), eq(MaterialDto.class)))
        .thenReturn(materialDto);

    MaterialPriceDto priceDto =
        new MaterialPriceDto(
            UUID.randomUUID(),
            "Area18",
            new BigDecimal("5.0"),
            new BigDecimal("7.0"),
            100,
            200,
            true,
            true);
    PageResponse<MaterialPriceDto> pageResponse =
        new PageResponse<>(List.of(priceDto), 0, 1000, 1, 1, Collections.emptyList());

    when(backendApiClient.get(
            eq("/api/v1/materials/" + id + "/prices?size=1000&sort=terminal.name,asc"),
            any(ParameterizedTypeReference.class)))
        .thenReturn(pageResponse);

    // Act
    String viewName = controller.getMaterialDetail(id, model);

    // Assert
    assertEquals("material-detail", viewName);
    MaterialDto material = (MaterialDto) model.getAttribute("material");
    assertNotNull(material);
    assertEquals("Gold", material.name());

    List<MaterialPriceDto> prices = (List<MaterialPriceDto>) model.getAttribute("prices");
    assertNotNull(prices);
    assertEquals(1, prices.size());
    assertEquals("Area18", prices.get(0).terminalName());
  }

  @Test
  void getMaterialDetail_ShouldHandleErrorAndAddEmptyDataToModel() {
    // Arrange
    BackendApiClient backendApiClient = mock(BackendApiClient.class);
    MaterialsPageController controller = new MaterialsPageController(backendApiClient);
    Model model = new ConcurrentModel();
    UUID id = UUID.randomUUID();

    when(backendApiClient.get(eq("/api/v1/materials/" + id), eq(MaterialDto.class)))
        .thenThrow(new RuntimeException("API Detail Error"));

    // Act
    String viewName = controller.getMaterialDetail(id, model);

    // Assert
    assertEquals("material-detail", viewName);
    assertEquals("error.material.details.load", model.getAttribute("error"));
    assertTrue(((List<MaterialPriceDto>) model.getAttribute("prices")).isEmpty());
  }
}
