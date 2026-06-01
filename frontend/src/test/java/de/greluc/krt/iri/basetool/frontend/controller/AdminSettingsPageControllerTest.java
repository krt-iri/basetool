package de.greluc.krt.iri.basetool.frontend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Unit tests for the job-order intake-SK persistence branch of {@link AdminSettingsPageController}.
 * The four base settings (age thresholds, refinery rounding, transfer fee) always PUT; the intake
 * SK is conditional because the backend setting is {@code @NotBlank} and cannot be cleared to blank
 * through this form.
 */
@ExtendWith(MockitoExtension.class)
class AdminSettingsPageControllerTest {

  private static final String INTAKE_URI = "/api/v1/settings/job_order.intake_special_command_id";

  @Mock private BackendApiClient backendApiClient;

  @InjectMocks private AdminSettingsPageController controller;

  @Test
  void updateSettings_persistsIntakeSk_whenAnSkIsSelected() {
    RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
    UUID sk = UUID.randomUUID();

    controller.updateSettings("30", 0L, "90", 0L, "UP", 0L, "0.5", 0L, sk.toString(), 0L, ra);

    verify(backendApiClient).put(eq(INTAKE_URI), any(), eq(SystemSettingDto.class));
  }

  @Test
  void updateSettings_skipsIntakeSk_whenSelectionIsBlank() {
    RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

    controller.updateSettings("30", 0L, "90", 0L, "UP", 0L, "0.5", 0L, "  ", 0L, ra);

    verify(backendApiClient, never()).put(eq(INTAKE_URI), any(), any());
  }
}
