package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.SystemSettingMapper;
import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.SystemSettingRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Key/value store for runtime-configurable settings (job-order age thresholds, refinery rounding
 * mode, …). Each row carries an optimistic-lock version so two admins editing the same key
 * concurrently can't silently overwrite each other.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

  private final SystemSettingRepository systemSettingRepository;
  private final SystemSettingMapper systemSettingMapper;

  /**
   * Returns all settings as DTOs.
   *
   * @return all settings as DTOs
   */
  @Transactional(readOnly = true)
  public List<SystemSettingDto> getAllSettings() {
    return systemSettingRepository.findAll().stream().map(systemSettingMapper::toDto).toList();
  }

  /**
   * Returns the setting DTO.
   *
   * @param key setting key (the table's PK)
   * @return the setting DTO
   * @throws NotFoundException when the key is not present
   */
  @Transactional(readOnly = true)
  public SystemSettingDto getSetting(String key) {
    return systemSettingRepository
        .findById(key)
        .map(systemSettingMapper::toDto)
        .orElseThrow(() -> new NotFoundException("Setting not found: " + key));
  }

  /**
   * Convenience accessor for code paths that only need the raw string value (e.g. parsing the
   * refinery rounding mode in the order pricing).
   *
   * @param key setting key
   * @return the string value, or empty when the key is absent
   */
  @Transactional(readOnly = true)
  public Optional<String> getSettingValue(String key) {
    return systemSettingRepository.findById(key).map(SystemSetting::getValue);
  }

  /**
   * Updates the value of a setting. Optimistic-lock check is explicit (the DTO carries the expected
   * version), not Hibernate's automatic check — the setting is identified by key (not by id) and
   * the form post pre-loads the version separately.
   *
   * @param key setting key
   * @param dto new value + expected version
   * @return the persisted setting DTO
   * @throws NotFoundException when the key is absent
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version no longer matches
   */
  @Transactional
  public SystemSettingDto updateSetting(String key, SystemSettingUpdateDto dto) {
    SystemSetting setting =
        systemSettingRepository
            .findById(key)
            .orElseThrow(() -> new NotFoundException("Setting not found: " + key));

    if (!setting.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          SystemSetting.class, key);
    }

    setting.setValue(dto.value());
    return systemSettingMapper.toDto(systemSettingRepository.save(setting));
  }
}
