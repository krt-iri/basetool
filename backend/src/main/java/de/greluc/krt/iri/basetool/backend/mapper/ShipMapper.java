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

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class})
public interface ShipMapper {
  ShipDto toDto(Ship ship);

  LocationDto locationToDto(Location location);

  ManufacturerDto manufacturerToDto(Manufacturer manufacturer);

  ShipTypeDto shipTypeToDto(ShipType shipType);
}
