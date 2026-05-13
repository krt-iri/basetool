package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.model.dto.AnnouncementDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Announcement entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface AnnouncementMapper {
  /** Maps an {@link Announcement} entity to its outbound DTO. */
  AnnouncementDto toDto(Announcement announcement);

  /** Builds a new {@link Announcement} entity from the inbound DTO. */
  Announcement toEntity(AnnouncementDto dto);
}
