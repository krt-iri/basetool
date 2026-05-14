package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the admin system-settings page ({@code /admin/settings}).
 *
 * <p>The page edits three independent system settings, each carrying its own optimistic-lock
 * version: the yellow/red age thresholds for job-order aging colors and the refinery rounding mode.
 * Every load fetches each setting individually so a single backend hiccup degrades to a default
 * value and a logged warning rather than blanking the entire page; the persisted version fields are
 * passed back through the form so the next save can use them.
 */
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminSettingsPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Loads the three system settings and exposes value+version pairs to the form template. Missing
   * settings fall back to documented defaults (30/90 days, rounding mode {@code UP}) so the page
   * never renders an empty input.
   *
   * @param model Thymeleaf model populated with the value+version pairs
   * @return the {@code admin-settings} view name
   */
  @GetMapping
  public String viewSettings(Model model) {
    int yellowDays = 30;
    int redDays = 90;
    Long yellowVersion = 0L;
    Long redVersion = 0L;
    String refineryRoundingMode = "UP";
    Long refineryRoundingVersion = 0L;

    try {
      SystemSettingDto yellowSetting =
          backendApiClient.get(
              "/api/v1/settings/job_order.age_yellow_days", SystemSettingDto.class);
      yellowDays = Integer.parseInt(yellowSetting.value());
      yellowVersion = yellowSetting.version();
    } catch (Exception e) {
      log.warn("Could not fetch yellow days setting");
    }

    try {
      SystemSettingDto redSetting =
          backendApiClient.get("/api/v1/settings/job_order.age_red_days", SystemSettingDto.class);
      redDays = Integer.parseInt(redSetting.value());
      redVersion = redSetting.version();
    } catch (Exception e) {
      log.warn("Could not fetch red days setting");
    }

    try {
      SystemSettingDto roundingSetting =
          backendApiClient.get("/api/v1/settings/refinery.rounding.mode", SystemSettingDto.class);
      refineryRoundingMode = roundingSetting.value();
      refineryRoundingVersion = roundingSetting.version();
    } catch (Exception e) {
      log.warn("Could not fetch refinery rounding mode setting");
    }

    model.addAttribute("ageYellowDays", yellowDays);
    model.addAttribute("ageYellowVersion", yellowVersion);
    model.addAttribute("ageRedDays", redDays);
    model.addAttribute("ageRedVersion", redVersion);
    model.addAttribute("refineryRoundingMode", refineryRoundingMode);
    model.addAttribute("refineryRoundingVersion", refineryRoundingVersion);

    return "admin-settings";
  }

  /**
   * Persists the three settings in one form submit.
   *
   * <p>Validates the relationship invariant {@code yellow < red} and that both are non-negative
   * before issuing any PUT — a violation short-circuits with a flash toast so the user sees the
   * error immediately and no partial update reaches the backend. Each setting is updated via its
   * own PUT carrying the form-supplied version (optimistic locking); a number-format error or any
   * other failure surfaces as a localized toast.
   *
   * @param ageYellowDaysStr yellow-aging threshold (parsed as int)
   * @param ageYellowVersion optimistic-lock version for the yellow setting
   * @param ageRedDaysStr red-aging threshold (parsed as int)
   * @param ageRedVersion optimistic-lock version for the red setting
   * @param refineryRoundingMode rounding mode (one of {@code UP}/{@code DOWN}/{@code HALF_UP}/…)
   * @param refineryRoundingVersion optimistic-lock version for the rounding setting
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/settings}
   */
  @PostMapping
  public String updateSettings(
      @RequestParam("ageYellowDays") String ageYellowDaysStr,
      @RequestParam("ageYellowVersion") Long ageYellowVersion,
      @RequestParam("ageRedDays") String ageRedDaysStr,
      @RequestParam("ageRedVersion") Long ageRedVersion,
      @RequestParam("refineryRoundingMode") String refineryRoundingMode,
      @RequestParam("refineryRoundingVersion") Long refineryRoundingVersion,
      RedirectAttributes redirectAttributes) {
    try {
      int yellowDays = Integer.parseInt(ageYellowDaysStr);
      int redDays = Integer.parseInt(ageRedDaysStr);

      if (yellowDays < 0 || redDays < 0 || yellowDays >= redDays) {
        redirectAttributes.addFlashAttribute("errorToast", "error.settings.invalid.values");
        return "redirect:/admin/settings";
      }

      backendApiClient.put(
          "/api/v1/settings/job_order.age_yellow_days",
          new SystemSettingUpdateDto(String.valueOf(yellowDays), ageYellowVersion),
          SystemSettingDto.class);
      backendApiClient.put(
          "/api/v1/settings/job_order.age_red_days",
          new SystemSettingUpdateDto(String.valueOf(redDays), ageRedVersion),
          SystemSettingDto.class);
      backendApiClient.put(
          "/api/v1/settings/refinery.rounding.mode",
          new SystemSettingUpdateDto(refineryRoundingMode, refineryRoundingVersion),
          SystemSettingDto.class);

      redirectAttributes.addFlashAttribute("successToast", "success.settings.update");
    } catch (NumberFormatException e) {
      redirectAttributes.addFlashAttribute("errorToast", "error.settings.invalid.format");
    } catch (Exception e) {
      log.error("Failed to update settings", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.settings.update.failed");
    }
    return "redirect:/admin/settings";
  }
}
