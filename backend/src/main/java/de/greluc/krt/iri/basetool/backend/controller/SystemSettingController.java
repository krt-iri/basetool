package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.service.SystemSettingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SystemSettingController {

  private final SystemSettingService systemSettingService;

  @GetMapping
  public List<SystemSettingDto> getAllSettings() {
    return systemSettingService.getAllSettings();
  }

  @GetMapping("/{key}")
  public SystemSettingDto getSetting(@PathVariable String key) {
    return systemSettingService.getSetting(key);
  }

  @PutMapping("/{key}")
  @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
  public SystemSettingDto updateSetting(
      @PathVariable String key, @Valid @RequestBody SystemSettingUpdateDto dto) {
    return systemSettingService.updateSetting(key, dto);
  }
}
