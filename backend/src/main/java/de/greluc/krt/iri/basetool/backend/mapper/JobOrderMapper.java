package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Job Order entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {
      InventoryItemMapper.class,
      UserMapper.class,
      MaterialMapper.class,
      JobOrderHandoverMapper.class,
      SquadronMapper.class
    })
public interface JobOrderMapper {

  /**
   * Maps a {@link JobOrder} entity to its outbound DTO. The DTO's legacy {@code squadron} string is
   * fed from {@code requestingSquadron.shorthand} so wire-shape clients that haven't migrated to
   * the structured {@code requestingSquadron} reference still see a non-null value after V88
   * dropped the entity-side legacy field. V90 (next-but-one release) removes the DTO component
   * entirely.
   */
  @Mapping(target = "squadron", source = "requestingSquadron.shorthand")
  JobOrderDto toDto(JobOrder jobOrder);

  /**
   * Maps a {@link JobOrderMaterial} child to its DTO. {@code currentStock} is owned by the service
   * layer (it queries the inventory at request time) and stays unmapped here.
   */
  @Mapping(target = "currentStock", ignore = true)
  JobOrderMaterialDto toDto(JobOrderMaterial material);

  /**
   * Maps a set of {@link JobOrderMaterial} children into a sorted DTO list: SCU-typed materials
   * first, then alphabetical by material name (case-insensitive). The deterministic order keeps the
   * materials table stable across reloads.
   */
  default List<JobOrderMaterialDto> mapAndSortMaterials(Set<JobOrderMaterial> materials) {
    if (materials == null) {
      return null;
    }
    return materials.stream()
        .map(this::toDto)
        .sorted(
            Comparator.<JobOrderMaterialDto, Integer>comparing(
                    m ->
                        (m.material() != null
                                && "SCU".equalsIgnoreCase(m.material().quantityType()))
                            ? 0
                            : 1)
                .thenComparing(
                    m ->
                        (m.material() != null && m.material().name() != null)
                            ? m.material().name()
                            : "",
                    String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
