package de.greluc.krt.iri.basetool.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCreateDto;
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
            false,
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

  @Test
  void createMaterial_delegatesToService_andReturnsMappedDto() {
    // Given a minimal create payload (POST /api/v1/materials)
    MaterialCreateDto request =
        new MaterialCreateDto(
            "Raw Ouratite", "RAW", "SCU", "manual", null, null, true, false, false, false, false);
    Material persisted = new Material();
    persisted.setId(UUID.randomUUID());
    persisted.setName("Raw Ouratite");
    persisted.setIsManualEntry(true);

    MaterialDto responseDto =
        new MaterialDto(
            persisted.getId(),
            "Raw Ouratite",
            "RAW",
            "SCU",
            "manual",
            null,
            null,
            false,
            false,
            false,
            true,
            false,
            true,
            0L);

    when(materialService.createMaterial(request)).thenReturn(persisted);
    when(materialMapper.toDto(persisted)).thenReturn(responseDto);

    // When
    MaterialDto result = materialController.createMaterial(request);

    // Then — the controller is a thin pass-through; the service does the heavy lifting
    assertEquals(responseDto, result);
    assertEquals(true, result.isManualEntry(), "Server-stamped audit flag is propagated");
  }
}
