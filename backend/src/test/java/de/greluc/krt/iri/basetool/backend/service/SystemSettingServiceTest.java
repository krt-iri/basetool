package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.SystemSettingMapper;
import de.greluc.krt.iri.basetool.backend.model.SystemSetting;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private SystemSettingService systemSettingService;

    private SystemSetting setting;
    private SystemSettingDto settingDto;

    @BeforeEach
    void setUp() {
        setting = new SystemSetting();
        setting.setId("test_key");
        setting.setValue("test_value");
        setting.setVersion(1L);

        settingDto = new SystemSettingDto("test_key", "test_value", 1L);
    }

    @Test
    void getSetting_ShouldReturnDto() {
        when(systemSettingRepository.findById("test_key")).thenReturn(Optional.of(setting));
        when(systemSettingMapper.toDto(setting)).thenReturn(settingDto);

        SystemSettingDto result = systemSettingService.getSetting("test_key");

        assertNotNull(result);
        assertEquals("test_value", result.value());
    }

    @Test
    void getSetting_NotFound_ShouldThrowException() {
        when(systemSettingRepository.findById("unknown_key")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> systemSettingService.getSetting("unknown_key"));
    }

    @Test
    void updateSetting_ShouldUpdateValue() {
        when(systemSettingRepository.findById("test_key")).thenReturn(Optional.of(setting));
        when(systemSettingRepository.save(any())).thenReturn(setting);
        when(systemSettingMapper.toDto(any())).thenReturn(new SystemSettingDto("test_key", "new_value", 2L));

        SystemSettingUpdateDto updateDto = new SystemSettingUpdateDto("new_value", 1L);
        SystemSettingDto result = systemSettingService.updateSetting("test_key", updateDto);

        assertEquals("new_value", result.value());
        verify(systemSettingRepository).save(setting);
    }

    @Test
    void updateSetting_OptimisticLocking_ShouldThrowException() {
        when(systemSettingRepository.findById("test_key")).thenReturn(Optional.of(setting));

        SystemSettingUpdateDto updateDto = new SystemSettingUpdateDto("new_value", 2L); // Wrong version

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> systemSettingService.updateSetting("test_key", updateDto));
        verify(systemSettingRepository, never()).save(any());
    }
}
