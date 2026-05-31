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
   * Maps a {@link JobOrder} entity to its outbound DTO. The legacy free-text {@code squadron} field
   * was removed from the DTO together with the V90 DROP COLUMN migration; clients consume the
   * structured {@code requestingSquadron} reference (and its {@code shorthand} sub-field) for a
   * human-readable label.
   *
   * <p>After R9 Step 2 the entity exposes {@code creatingOrgUnit} / {@code requestingOrgUnit}
   * (typed {@code OrgUnit}); the DTO still publishes {@code creatingSquadron} / {@code
   * requestingSquadron} as {@code SquadronReferenceDto} for API stability. The two explicit
   * mappings below route both fields through {@code SquadronMapper.orgUnitToReferenceDto}, which
   * projects either kind — a Staffel or a Spezialkommando — into the slim reference
   * (id/name/shorthand), so an SK creating or requesting on behalf now surfaces its SK badge
   * instead of a blank cell.
   *
   * @param jobOrder the entity to project; {@code null} returns {@code null}.
   * @return the populated outbound DTO.
   */
  @Mapping(target = "creatingSquadron", source = "creatingOrgUnit")
  @Mapping(target = "requestingSquadron", source = "requestingOrgUnit")
  @Mapping(target = "items", ignore = true)
  @Mapping(target = "aggregatedMaterials", ignore = true)
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
