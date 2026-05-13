package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Announcement;
import de.greluc.krt.iri.basetool.backend.model.dto.AnnouncementDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface AnnouncementMapper {
  AnnouncementDto toDto(Announcement announcement);

  Announcement toEntity(AnnouncementDto dto);
}
