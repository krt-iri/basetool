package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SystemSettingMapper {
    SystemSettingDto toDto(SystemSetting setting);
}
