package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.backend.service.MaterialService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaterialControllerTest {

  @Mock private MaterialService materialService;

  @Mock private MaterialMapper materialMapper;

  @InjectMocks private MaterialController materialController;

  @Test
  void getJobOrderMaterials_ShouldReturnOnlyJobOrderMaterials() {
    // Given
    Material mat = new Material();
    mat.setId(UUID.randomUUID());
    mat.setName("Agricium");
    mat.setType(MaterialType.RAW);
    mat.setIsJobOrder(true);

    MaterialDto dto =
        new MaterialDto(
            mat.getId(),
            "Agricium",
            "RAW",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            0L);

    when(materialService.getAllJobOrderMaterials()).thenReturn(List.of(mat));
    when(materialMapper.toDto(mat)).thenReturn(dto);

    // When
    List<MaterialDto> result = materialController.getJobOrderMaterials();

    // Then
    assertEquals(1, result.size());
    assertEquals("Agricium", result.get(0).name());
    assertEquals(true, result.get(0).isJobOrder());
  }
}
