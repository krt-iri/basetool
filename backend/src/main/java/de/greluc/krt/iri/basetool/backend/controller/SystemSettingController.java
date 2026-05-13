package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.service.SystemSettingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Public/admin REST surface over the {@code system_setting} key-value store. Reads are public (the
 * frontend's home page reads the announcement / aging thresholds without authentication); writes
 * are restricted to ADMIN/OFFICER and carry an optimistic-lock version.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SystemSettingController {

  private final SystemSettingService systemSettingService;

  /**
   * @return every system setting as a DTO
   */
  @GetMapping
  public List<SystemSettingDto> getAllSettings() {
    return systemSettingService.getAllSettings();
  }

  /**
   * @param key setting key (table primary key)
   * @return the setting DTO
   */
  @GetMapping("/{key}")
  public SystemSettingDto getSetting(@PathVariable String key) {
    return systemSettingService.getSetting(key);
  }

  /**
   * Updates a single setting. Optimistic-lock check is explicit (version in the DTO body).
   *
   * @param key setting key
   * @param dto update payload (value + expected version)
   * @return the persisted DTO
   */
  @PutMapping("/{key}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public SystemSettingDto updateSetting(
      @PathVariable String key, @Valid @RequestBody SystemSettingUpdateDto dto) {
    return systemSettingService.updateSetting(key, dto);
  }
}
