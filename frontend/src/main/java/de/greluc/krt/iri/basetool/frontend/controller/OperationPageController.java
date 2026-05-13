package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.form.OperationForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the operations pages ({@code /operations} list and {@code
 * /operations/{id}} detail).
 *
 * <p>Operations are an umbrella over missions — the detail page renders the operation header,
 * embedded missions paginated separately, and the operation-level finance and payout summaries. The
 * {@code canEdit} flag computed in {@link #operationDetails} mirrors the backend's
 * {@code @PreAuthorize("hasRole('MISSION_MANAGER')")} so the template can disable inputs for users
 * who would just bounce off a 403 on submit — without leaking any role logic into the service
 * layer.
 */
@Controller
@RequestMapping("/operations")
@RequiredArgsConstructor
@Slf4j
public class OperationPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the paginated operations list.
   *
   * @param page zero-based page index
   * @param size page size (default 20)
   * @param model Thymeleaf model populated with the page content and metadata
   * @return the {@code operations-index} view name
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public String listOperations(
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size,
      Model model) {
    try {
      PageResponse<OperationDto> operationsPage =
          backendApiClient.get(
              "/api/v1/operations?page=" + page + "&size=" + size + "&sort=createdAt,desc",
              new ParameterizedTypeReference<PageResponse<OperationDto>>() {},
              false);
      model.addAttribute("operations", operationsPage.content());
      model.addAttribute("operationsPage", operationsPage);
    } catch (Exception e) {
      log.error("Error loading operations", e);
      model.addAttribute("error", "error.operations.load");
    }
    return "operations-index";
  }

  /**
   * Renders the operation detail page. Pulls operation, embedded missions, finance and payouts in
   * sequence; any backend failure aborts the render and redirects back to the list with a flash
   * error. Computes {@code canEdit} at the HTTP boundary by reading the authorities off the {@link
   * Authentication} object — keeps the template free of role-expression checks and mirrors what the
   * backend's PUT endpoint will accept.
   *
   * @param id operation id
   * @param page zero-based page index for the embedded missions table
   * @param size page size for the embedded missions table (default 10)
   * @param authentication current user's authentication (used for {@code canEdit})
   * @param model Thymeleaf model populated with operation, missions, finance and payouts
   * @return the {@code operation-detail} view name, or redirect on backend failure
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public String operationDetails(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "10") Integer size,
      Authentication authentication,
      Model model) {
    try {
      OperationDto operation =
          backendApiClient.get("/api/v1/operations/" + id, OperationDto.class, false);
      model.addAttribute("operation", operation);

      PageResponse<MissionListDto> missionsPage =
          backendApiClient.get(
              "/api/v1/missions/search?operationId="
                  + id
                  + "&page="
                  + page
                  + "&size="
                  + size
                  + "&sort=plannedStartTime,asc",
              new ParameterizedTypeReference<PageResponse<MissionListDto>>() {},
              false);
      model.addAttribute("missions", missionsPage.content());
      model.addAttribute("missionsPage", missionsPage);

      OperationFinanceDto operationFinance =
          backendApiClient.get(
              "/api/v1/operations/" + id + "/finances", OperationFinanceDto.class, false);
      model.addAttribute("operationFinance", operationFinance);

      List<OperationPayoutDto> payouts =
          backendApiClient.get(
              "/api/v1/operations/" + id + "/payouts",
              new ParameterizedTypeReference<List<OperationPayoutDto>>() {},
              false);
      model.addAttribute("operationPayouts", payouts);

      // Resolved at the HTTP boundary so the template stays free of inline
      // role-expression checks. The backend's PUT /api/v1/operations/{id}
      // requires ROLE_MISSION_MANAGER (or any role that reaches it via the
      // hierarchy — ADMIN, OFFICER) AND the same role is granted by the
      // app_user.is_mission_manager flag through the JWT-converter, so the
      // role check here matches what the backend enforces.
      model.addAttribute("canEdit", hasMissionManagerRole(authentication));

    } catch (Exception e) {
      log.error("Error loading operation details", e);
      model.addAttribute("error", "error.operation.load");
      return "redirect:/operations";
    }
    return "operation-detail";
  }

  private static boolean hasMissionManagerRole(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(
            role ->
                "ROLE_ADMIN".equals(role)
                    || "ROLE_OFFICER".equals(role)
                    || "ROLE_MISSION_MANAGER".equals(role));
  }

  /**
   * Creates a new operation. {@code MISSION_MANAGER} role is required (admin/officer satisfy it via
   * the role hierarchy).
   *
   * @param form operation form
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /operations}
   */
  @PostMapping("/create")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public String createOperation(
      @ModelAttribute OperationForm form, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post("/api/v1/operations", form, Void.class);
      redirectAttributes.addFlashAttribute("successMessage", "operation.create.success");
    } catch (Exception e) {
      log.error("Error creating operation", e);
      redirectAttributes.addFlashAttribute("errorMessage", "operation.create.error");
    }
    return "redirect:/operations";
  }

  /**
   * Updates an operation. A {@code 409 Conflict} from the backend is mapped to the
   * optimistic-locking flash message; any other failure to the generic update-error message.
   *
   * @param id operation id
   * @param form operation form (carries the optimistic-lock version)
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /operations}
   */
  @PostMapping("/{id}/update")
  @PreAuthorize("hasRole('MISSION_MANAGER')")
  public String updateOperation(
      @PathVariable @NotNull UUID id,
      @ModelAttribute OperationForm form,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.put("/api/v1/operations/" + id, form, Void.class);
      redirectAttributes.addFlashAttribute("successMessage", "operation.update.success");
    } catch (WebClientResponseException.Conflict e) {
      log.warn("Optimistic locking failure updating operation: {}", id);
      redirectAttributes.addFlashAttribute("errorMessage", "error.optimistic.locking");
    } catch (Exception e) {
      log.error("Error updating operation", e);
      redirectAttributes.addFlashAttribute("errorMessage", "operation.update.error");
    }
    return "redirect:/operations";
  }

  /**
   * Deletes an operation. Admin-only — narrower than the class-level read access.
   *
   * @param id operation id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /operations}
   */
  @PostMapping("/{id}/delete")
  @PreAuthorize("hasRole('ADMIN')")
  public String deleteOperation(
      @PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/operations/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successMessage", "operation.delete.success");
    } catch (Exception e) {
      log.error("Error deleting operation", e);
      redirectAttributes.addFlashAttribute("errorMessage", "operation.delete.error");
    }
    return "redirect:/operations";
  }
}
