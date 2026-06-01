package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.SpecialCommandDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SystemSettingUpdateDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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
 * <p>The page edits four independent system settings, each carrying its own optimistic-lock
 * version: the yellow/red age thresholds for job-order aging colors, the refinery rounding mode and
 * the in-game banking transfer-fee rate applied to per-participant operation payouts. Every load
 * fetches each setting individually so a single backend hiccup degrades to a default value and a
 * logged warning rather than blanking the entire page; the persisted version fields are passed back
 * through the form so the next save can use them.
 *
 * <p>The transfer-fee rate is stored in the DB as a decimal fraction ({@code 0.005} = 0.5%) so the
 * consumer ({@code OperationService}) can multiply directly. For the form we convert it to a
 * human-friendly percentage ({@code 0.5}) on load and back to a fraction on save — admins shouldn't
 * have to count leading zeros.
 */
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsPageController {

  /** Decimal scale used when converting between DB fraction and form percentage. */
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  /**
   * System-setting key holding the UUID of the designated intake Spezialkommando that anonymous /
   * guest Job-Order creations are routed to. Seeded empty by Flyway V128; an admin picks the SK
   * here.
   */
  private static final String INTAKE_SK_SETTING_KEY = "job_order.intake_special_command_id";

  /**
   * Display default for the transfer-fee rate (percent) used when the backend lookup fails. Kept in
   * sync with {@code OperationService.DEFAULT_TRANSFER_FEE_RATE} (0.005 = 0.5%) so the form never
   * renders blank.
   */
  private static final BigDecimal DEFAULT_TRANSFER_FEE_PERCENT = new BigDecimal("0.5");

  private final BackendApiClient backendApiClient;

  /**
   * Loads all admin-tunable system settings and exposes value+version pairs to the form template.
   * Missing settings fall back to documented defaults (30/90 days, rounding mode {@code UP}, 0.5%
   * transfer fee) so the page never renders an empty input. The transfer-fee rate is converted from
   * DB-side decimal fraction to display-side percent so the admin sees {@code 0.5} instead of
   * {@code 0.005}.
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

    BigDecimal transferFeePercent = DEFAULT_TRANSFER_FEE_PERCENT;
    Long transferFeeVersion = 0L;
    try {
      SystemSettingDto feeSetting =
          backendApiClient.get(
              "/api/v1/settings/operation.transfer_fee_rate", SystemSettingDto.class);
      // Convert DB fraction (e.g. "0.005") to display percent (e.g. "0.5"). Strip trailing
      // zeros so "0.50" doesn't render as "0.5000" in the input.
      transferFeePercent =
          new BigDecimal(feeSetting.value()).multiply(ONE_HUNDRED).stripTrailingZeros();
      if (transferFeePercent.scale() < 0) {
        transferFeePercent = transferFeePercent.setScale(0, RoundingMode.UNNECESSARY);
      }
      transferFeeVersion = feeSetting.version();
    } catch (Exception e) {
      log.warn("Could not fetch operation transfer fee rate setting");
    }

    model.addAttribute("ageYellowDays", yellowDays);
    model.addAttribute("ageYellowVersion", yellowVersion);
    model.addAttribute("ageRedDays", redDays);
    model.addAttribute("ageRedVersion", redVersion);
    model.addAttribute("refineryRoundingMode", refineryRoundingMode);
    model.addAttribute("refineryRoundingVersion", refineryRoundingVersion);
    model.addAttribute("transferFeePercent", transferFeePercent.toPlainString());
    model.addAttribute("transferFeeVersion", transferFeeVersion);
    model.addAttribute("squadrons", fetchSquadronsForPromotionToggle());

    String intakeSpecialCommandId = "";
    Long intakeSpecialCommandVersion = 0L;
    try {
      SystemSettingDto intakeSetting =
          backendApiClient.get("/api/v1/settings/" + INTAKE_SK_SETTING_KEY, SystemSettingDto.class);
      intakeSpecialCommandId = intakeSetting.value() == null ? "" : intakeSetting.value();
      intakeSpecialCommandVersion = intakeSetting.version();
    } catch (Exception e) {
      log.warn("Could not fetch job-order intake special-command setting");
    }
    model.addAttribute("intakeSpecialCommandId", intakeSpecialCommandId);
    model.addAttribute("intakeSpecialCommandVersion", intakeSpecialCommandVersion);
    model.addAttribute("specialCommands", fetchSpecialCommands());

    return "admin-settings";
  }

  /**
   * Loads every Spezialkommando (alphabetical) for the job-order intake-SK dropdown on the
   * admin-settings page. A backend failure degrades to an empty list with a logged warning so the
   * rest of the page still renders.
   *
   * @return Spezialkommandos sorted by name, never {@code null}.
   */
  private List<SpecialCommandDto> fetchSpecialCommands() {
    try {
      PageResponse<SpecialCommandDto> page =
          backendApiClient.get(
              "/api/v1/special-commands?size=1000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<SpecialCommandDto>>() {});
      if (page == null || page.content() == null) {
        return List.of();
      }
      return page.content().stream()
          .sorted(
              Comparator.comparing(
                  s -> s.name() == null ? "" : s.name(), String.CASE_INSENSITIVE_ORDER))
          .toList();
    } catch (Exception e) {
      log.warn(
          "Could not fetch special commands for admin-settings intake picker: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Loads every active squadron (alphabetical) for the "Beförderungssystem pro Staffel" toggle
   * section on the admin-settings page. Inactive (soft-deleted) squadrons are filtered out — the
   * admin re-activates them through the existing squadron CRUD before toggling features. A backend
   * failure degrades to an empty list with a logged warning so the rest of the page still renders.
   *
   * @return active squadrons sorted by name, never {@code null}.
   */
  private List<SquadronDto> fetchSquadronsForPromotionToggle() {
    try {
      PageResponse<SquadronDto> page =
          backendApiClient.get(
              "/api/v1/squadrons?size=1000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<SquadronDto>>() {});
      if (page == null || page.content() == null) {
        return List.of();
      }
      return page.content().stream()
          .sorted(
              Comparator.comparing(
                  s -> s.name() == null ? "" : s.name(), String.CASE_INSENSITIVE_ORDER))
          .toList();
    } catch (Exception e) {
      log.warn("Could not fetch squadrons for admin-settings promotion toggle: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Persists the four settings in one form submit.
   *
   * <p>Validates the relationship invariants ({@code yellow < red}, both non-negative; transfer fee
   * in {@code [0, 100)} as percent) before issuing any PUT — a violation short-circuits with a
   * flash toast so the user sees the error immediately and no partial update reaches the backend.
   * Each setting is updated via its own PUT carrying the form-supplied version (optimistic
   * locking); a number-format error or any other failure surfaces as a localized toast. The
   * transfer-fee field is converted from the human percent input to the DB-side decimal fraction
   * before posting.
   *
   * @param ageYellowDaysStr yellow-aging threshold (parsed as int)
   * @param ageYellowVersion optimistic-lock version for the yellow setting
   * @param ageRedDaysStr red-aging threshold (parsed as int)
   * @param ageRedVersion optimistic-lock version for the red setting
   * @param refineryRoundingMode rounding mode (one of {@code UP}/{@code DOWN}/{@code HALF_UP}/…)
   * @param refineryRoundingVersion optimistic-lock version for the rounding setting
   * @param transferFeePercentStr in-game banking transfer fee as a percentage (e.g. {@code 0.5})
   * @param transferFeeVersion optimistic-lock version for the transfer-fee setting
   * @param intakeSpecialCommandId UUID of the job-order intake Spezialkommando; blank leaves the
   *     current value untouched (the value cannot be cleared back to blank via this form)
   * @param intakeSpecialCommandVersion optimistic-lock version for the intake-SK setting
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
      @RequestParam("transferFeePercent") String transferFeePercentStr,
      @RequestParam("transferFeeVersion") Long transferFeeVersion,
      @RequestParam(name = "intakeSpecialCommandId", required = false, defaultValue = "")
          String intakeSpecialCommandId,
      @RequestParam(name = "intakeSpecialCommandVersion", required = false, defaultValue = "0")
          Long intakeSpecialCommandVersion,
      RedirectAttributes redirectAttributes) {
    try {
      int yellowDays = Integer.parseInt(ageYellowDaysStr);
      int redDays = Integer.parseInt(ageRedDaysStr);

      if (yellowDays < 0 || redDays < 0 || yellowDays >= redDays) {
        redirectAttributes.addFlashAttribute("errorToast", "error.settings.invalid.values");
        return "redirect:/admin/settings";
      }

      BigDecimal transferFeePercent = new BigDecimal(transferFeePercentStr.trim());
      if (transferFeePercent.signum() < 0 || transferFeePercent.compareTo(ONE_HUNDRED) >= 0) {
        redirectAttributes.addFlashAttribute("errorToast", "error.settings.invalid.values");
        return "redirect:/admin/settings";
      }
      // Convert human-friendly percent to DB-side decimal fraction (0.5% -> 0.005).
      BigDecimal transferFeeRate = transferFeePercent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);

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
      backendApiClient.put(
          "/api/v1/settings/operation.transfer_fee_rate",
          new SystemSettingUpdateDto(
              transferFeeRate.stripTrailingZeros().toPlainString(), transferFeeVersion),
          SystemSettingDto.class);

      // Only persist the intake SK when an SK is actually selected. The backend setting is
      // @NotBlank, so a blank submit (no SK chosen yet) is treated as "leave unchanged" rather than
      // an attempt to clear it.
      if (intakeSpecialCommandId != null && !intakeSpecialCommandId.isBlank()) {
        backendApiClient.put(
            "/api/v1/settings/" + INTAKE_SK_SETTING_KEY,
            new SystemSettingUpdateDto(intakeSpecialCommandId.trim(), intakeSpecialCommandVersion),
            SystemSettingDto.class);
      }

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
