package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.SystemSettingMapper;
import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final SystemSettingMapper systemSettingMapper;

    @Transactional(readOnly = true)
    public List<SystemSettingDto> getAllSettings() {
        return systemSettingRepository.findAll().stream()
                .map(systemSettingMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SystemSettingDto getSetting(String key) {
        return systemSettingRepository.findById(key)
                .map(systemSettingMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Setting not found: " + key));
    }
    
    @Transactional(readOnly = true)
    public Optional<String> getSettingValue(String key) {
        return systemSettingRepository.findById(key).map(SystemSetting::getValue);
    }

    @Transactional
    public SystemSettingDto updateSetting(String key, SystemSettingUpdateDto dto) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseThrow(() -> new NotFoundException("Setting not found: " + key));

        if (!setting.getVersion().equals(dto.version())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SystemSetting.class, key);
        }

        setting.setValue(dto.value());
        return systemSettingMapper.toDto(systemSettingRepository.save(setting));
    }
}
