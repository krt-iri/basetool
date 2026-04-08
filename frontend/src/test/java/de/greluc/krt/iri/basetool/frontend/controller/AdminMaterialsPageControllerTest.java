package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminMaterialsPageControllerTest {

    @Test
    void listMaterials_ShouldSortListsAscendingByName() {
        // Arrange
        BackendApiClient backendApiClient = mock(BackendApiClient.class);
        AdminMaterialsPageController controller = new AdminMaterialsPageController(backendApiClient);
        Model model = new ConcurrentModel();

        // Data for Materials
        List<MaterialDto> materials = new ArrayList<>();
        materials.add(new MaterialDto(UUID.randomUUID(), 1, "Alpha", "RAW", "SCU", "Desc", null, null, false, false, false, 0L));
        materials.add(new MaterialDto(UUID.randomUUID(), 4, "Delta", "REFINED", "SCU", "Desc", null, null, false, false, false, 0L));
        materials.add(new MaterialDto(UUID.randomUUID(), 3, "Charlie", "RAW", "SCU", "Desc", null, null, false, false, false, 0L));
        materials.add(new MaterialDto(UUID.randomUUID(), 5, "Echo", "NO_REFINE", "SCU", "Desc", null, null, false, false, false, 0L));
        materials.add(new MaterialDto(UUID.randomUUID(), 2, "Bravo", "RAW", "SCU", "Desc", null, null, false, false, false, 0L));

        PageResponse<MaterialDto> materialsPage = new PageResponse<>(materials, 0, 1000, materials.size(), 1, List.of("name,asc"));

        when(backendApiClient.get(
                eq("/api/v1/materials?size=1000&sort=name,asc"),
                any(ParameterizedTypeReference.class)
        )).thenReturn(materialsPage);

        // Act
        controller.listMaterials(model);

        // Assert
        List<MaterialDto> sortedMaterials = (List<MaterialDto>) model.getAttribute("materials");
        assertEquals(5, sortedMaterials.size());
        assertEquals("Alpha", sortedMaterials.get(0).name());
        assertEquals("Bravo", sortedMaterials.get(1).name());
        assertEquals("Charlie", sortedMaterials.get(2).name());
        assertEquals("Delta", sortedMaterials.get(3).name());
        assertEquals("Echo", sortedMaterials.get(4).name());
        
        List<MaterialDto> refinedMats = (List<MaterialDto>) model.getAttribute("refinedMaterials");
        assertEquals(1, refinedMats.size());
        assertEquals("Delta", refinedMats.get(0).name());
    }
}
