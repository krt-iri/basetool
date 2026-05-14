package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between System Setting entities and DTOs. */
@Mapper(componentModel = "spring")
public interface SystemSettingMapper {
  /** Maps a {@link SystemSetting} entity to its outbound DTO. */
  SystemSettingDto toDto(SystemSetting setting);
}
