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

/** MapStruct mapper between Ship entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class})
public interface ShipMapper {
  /** Maps a {@link Ship} entity to its outbound DTO. */
  ShipDto toDto(Ship ship);

  /** Nested mapping for the ship's stationing {@link Location}. */
  LocationDto locationToDto(Location location);

  /** Nested mapping for the ship's {@link Manufacturer}. */
  ManufacturerDto manufacturerToDto(Manufacturer manufacturer);

  /**
   * Lightweight nested mapping for the ship's {@link ShipType}. Intentionally narrower than the
   * full UEX-sourced ShipType view to avoid leaking irrelevant fields through the {@link Ship}
   * boundary.
   */
  ShipTypeDto shipTypeToDto(ShipType shipType);
}
