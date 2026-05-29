package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipTypeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Ship entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, SquadronMapper.class})
public interface ShipMapper {
  /**
   * Maps a {@link Ship} entity to its outbound DTO.
   *
   * <p>After R9 Step 2 the ship entity exposes {@code owningOrgUnit} (typed {@code OrgUnit}); the
   * DTO still publishes {@code owningSquadron} as {@code SquadronReferenceDto} for API stability.
   * The explicit mapping routes the source through {@code SquadronMapper.orgUnitToReferenceDto} so
   * SK-owned ships surface as {@code null} on the wire while Staffel-owned ones continue to project
   * as before.
   *
   * @param ship the ship entity to project; {@code null} returns {@code null}.
   * @return the populated ship DTO.
   */
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  ShipDto toDto(Ship ship);

  /** Nested mapping for the ship's stationing {@link Location}. */
  LocationDto locationToDto(Location location);

  /** Nested mapping for the ship's {@link Manufacturer}. */
  ManufacturerDto manufacturerToDto(Manufacturer manufacturer);

  /**
   * Lightweight nested mapping for the ship's {@link ShipType}. Intentionally narrower than the
   * full UEX-sourced ShipType view to avoid leaking irrelevant fields through the {@link Ship}
   * boundary.
   *
   * <p>R9 Step 2: the {@code description} wire field is sourced from the rich {@code descriptionDe}
   * / {@code descriptionEn} columns (German preferred, English fallback), not the legacy
   * synthesised {@code ship_type.description} column — which is no longer written and is dropped in
   * R9 Step 4.
   */
  @Mapping(
      target = "description",
      expression =
          "java(shipType.getDescriptionDe() != null ? shipType.getDescriptionDe()"
              + " : shipType.getDescriptionEn())")
  ShipTypeDto shipTypeToDto(ShipType shipType);
}
