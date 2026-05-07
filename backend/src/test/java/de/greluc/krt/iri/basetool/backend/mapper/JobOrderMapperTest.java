package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobOrderMapperTest {

    @Test
    void mapAndSortMaterials_ShouldSortScuFirstThenAlphabetically() {
        // Given
        JobOrderMapper mapper = new JobOrderMapper() {
            @Override
            public JobOrderDto toDto(JobOrder jobOrder) {
                return null;
            }

            @Override
            public JobOrderMaterialDto toDto(JobOrderMaterial material) {
                MaterialDto matDto = new MaterialDto(
                        material.getMaterial() != null ? material.getMaterial().getId() : null,
                        material.getMaterial() != null ? material.getMaterial().getName() : null,
                        null,
                        material.getMaterial() != null && material.getMaterial().getQuantityType() != null ? material.getMaterial().getQuantityType().name() : null,
                        null, null, null, false, false, false, false, false, 1L
                );
                return new JobOrderMaterialDto(material.getId(), matDto, material.getMinQuality(), material.getAmount(), 0.0, 1L);
            }
        };

        Material m1 = new Material(); m1.setName("Gold"); m1.setQuantityType(QuantityType.SCU);
        Material m2 = new Material(); m2.setName("Iron"); m2.setQuantityType(QuantityType.PIECE);
        Material m3 = new Material(); m3.setName("Silver"); m3.setQuantityType(QuantityType.SCU);
        Material m4 = new Material(); m4.setName("Copper"); m4.setQuantityType(QuantityType.PIECE);

        JobOrderMaterial jm1 = new JobOrderMaterial(); jm1.setMaterial(m1); jm1.setId(UUID.randomUUID());
        JobOrderMaterial jm2 = new JobOrderMaterial(); jm2.setMaterial(m2); jm2.setId(UUID.randomUUID());
        JobOrderMaterial jm3 = new JobOrderMaterial(); jm3.setMaterial(m3); jm3.setId(UUID.randomUUID());
        JobOrderMaterial jm4 = new JobOrderMaterial(); jm4.setMaterial(m4); jm4.setId(UUID.randomUUID());

        Set<JobOrderMaterial> materials = Set.of(jm1, jm2, jm3, jm4);

        // When
        List<JobOrderMaterialDto> result = mapper.mapAndSortMaterials(materials);

        // Then
        assertEquals(4, result.size());
        assertEquals("Gold", result.get(0).material().name());
        assertEquals("Silver", result.get(1).material().name());
        assertEquals("Copper", result.get(2).material().name());
        assertEquals("Iron", result.get(3).material().name());
    }
}
