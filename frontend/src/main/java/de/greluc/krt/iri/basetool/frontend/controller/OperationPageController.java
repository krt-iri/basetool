package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutStatusUpdateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OrgUnitMembershipOptionDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.OperationForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
   * Renders the paginated, filtered operations list. Mirrors the missions overview filter contract
   * within the limits of the operation aggregate: free-text {@code search} matches name +
   * description, {@code showPast} flips the default status filter from {@code PLANNED}+{@code
   * ACTIVE} to the full set, and the {@code start}/{@code end} time range filters on the
   * operation's derived span — an operation has no {@code plannedStartTime} of its own, so the
   * backend bounds {@code start} against the planned start of the earliest linked mission and
   * {@code end} against the planned end of the latest linked mission. The two bounds arrive as
   * ISO-8601 instants assembled client-side by {@code datetime-splitter.js} and are forwarded
   * verbatim. When {@code fragment=results} is supplied the controller returns just the results
   * fragment so the client-side AJAX filter can patch the list in place without a full page reload.
   *
   * @param search free-text query, may be {@code null}
   * @param start inclusive lower bound (ISO-8601) on the earliest linked mission's planned start,
   *     may be {@code null}/blank
   * @param end inclusive upper bound (ISO-8601) on the latest linked mission's planned end, may be
   *     {@code null}/blank
   * @param showPast when {@code true} (and authenticated), include COMPLETED and CANCELED
   * @param page zero-based page index
   * @param size page size (default 20)
   * @param fragment when equal to {@code "results"}, render only the results fragment
   * @param model Thymeleaf model populated with the page content and metadata
   * @param principal current OIDC user (null for guests — the endpoint stays auth-only via
   *     {@code @PreAuthorize}, this is used to decide whether {@code showPast} is honoured)
   * @return the {@code operations-index} view name, or the results fragment for AJAX
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public String listOperations(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false, defaultValue = "false") boolean showPast,
      @RequestParam(required = false, defaultValue = "0") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer size,
      @RequestParam(required = false) String fragment,
      Model model,
      @AuthenticationPrincipal OidcUser principal) {
    StringBuilder uri = new StringBuilder("/api/v1/operations/search?");
    if (search != null && !search.isBlank()) {
      uri.append("query=").append(URLEncoder.encode(search, StandardCharsets.UTF_8)).append("&");
    }
    if (start != null && !start.isBlank()) {
      uri.append("start=").append(URLEncoder.encode(start, StandardCharsets.UTF_8)).append("&");
    }
    if (end != null && !end.isBlank()) {
      uri.append("end=").append(URLEncoder.encode(end, StandardCharsets.UTF_8)).append("&");
    }
    uri.append("page=").append(page).append("&");
    uri.append("size=").append(size).append("&");
    uri.append("sort=createdAt,desc&");

    boolean effectiveShowPast = showPast && principal != null;
    if (effectiveShowPast) {
      uri.append("status=PLANNED&status=ACTIVE&status=COMPLETED&status=CANCELED&");
    } else {
      uri.append("status=PLANNED&status=ACTIVE&");
    }

    try {
      PageResponse<OperationDto> operationsPage =
          backendApiClient.get(
              uri.toString(),
              new ParameterizedTypeReference<PageResponse<OperationDto>>() {},
              false);
      model.addAttribute("operations", operationsPage.content());
      model.addAttribute("operationsPage", operationsPage);
      model.addAttribute("search", search);
      model.addAttribute("start", start);
      model.addAttribute("end", end);
      model.addAttribute("showPast", effectiveShowPast);
    } catch (Exception e) {
      log.error("Error loading operations", e);
      model.addAttribute("error", "error.operations.load");
    }
    if (fragment != null && "results".equalsIgnoreCase(fragment)) {
      return "operations-index :: operationsResults";
    }
    model.addAttribute("ownerOptions", fetchCallerMembershipOptions(principal));
    return "operations-index";
  }

  /**
   * Fetches the caller's OrgUnit memberships for the R5.d.e owner-picker fragment on the
   * operation-create modal. Operations have no explicit owner field — the actor (caller) is the
   * implicit owner — so the picker reflects the caller's own memberships. Returns an empty list for
   * anonymous callers or on backend hiccup; the fragment collapses to its hidden state in either
   * case.
   *
   * @param principal authenticated OIDC user, may be {@code null} for guests.
   * @return picker options or empty list; never {@code null}.
   */
  private List<OrgUnitMembershipOptionDto> fetchCallerMembershipOptions(OidcUser principal) {
    if (principal == null) {
      return List.of();
    }
    try {
      UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
      if (me == null || me.id() == null) {
        return List.of();
      }
      List<OrgUnitMembershipOptionDto> options =
          backendApiClient.get(
              "/api/v1/users/" + me.id() + "/memberships", new ParameterizedTypeReference<>() {});
      return options != null ? options : List.of();
    } catch (Exception e) {
      log.warn("Failed to fetch memberships for operation-create owner-picker", e);
      return List.of();
    }
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
      // The "Bezahlt"-checkbox is asymmetric: any mission manager can set
      // it to paid, but only an officer or admin may clear it back to
      // unpaid. The template uses this flag to disable an already-checked
      // checkbox for plain mission managers — mirrors the asymmetric
      // @PreAuthorize on the backend's payouts/paid-out endpoint.
      model.addAttribute("canUnsetPaidOut", hasOfficerOrAdminRole(authentication));

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

  private static boolean hasOfficerOrAdminRole(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_OFFICER".equals(role));
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
   * AJAX endpoint behind the per-row "Bezahlt" checkbox in the Auszahlungen panel. Proxies the call
   * straight to {@code PUT /api/v1/operations/{id}/payouts/paid-out}, forwarding the operation id
   * from the URL and the participant key plus new flag from the request body. The backend
   * re-renders the affected payout row and we hand it back as JSON so the client can patch a single
   * table row without refetching the whole breakdown.
   *
   * <p>Authorization is asymmetric and mirrors the backend: any mission manager (or higher via the
   * role hierarchy) can set {@code paidOut=true}, but only ADMIN or OFFICER can clear it back to
   * {@code false}. The SpEL guard returns 403 for a plain mission manager attempting to uncheck the
   * box; the JS handler surfaces this as the {@code operation.payout.paid.forbidden} toast.
   *
   * @param id operation id (from the URL)
   * @param request participant key + new {@code paidOut} value
   * @return refreshed payout row on success, or a 403 / 500 mirroring the backend status
   */
  @PostMapping("/{id}/payouts/paid-out")
  @PreAuthorize(
      "hasRole('MISSION_MANAGER') and (#request.paidOut() or hasAnyRole('ADMIN', 'OFFICER'))")
  @ResponseBody
  public ResponseEntity<OperationPayoutDto> updatePayoutStatus(
      @PathVariable @NotNull UUID id, @RequestBody OperationPayoutStatusUpdateDto request) {
    try {
      OperationPayoutDto updated =
          backendApiClient.put(
              "/api/v1/operations/" + id + "/payouts/paid-out",
              request,
              OperationPayoutDto.class,
              false);
      return ResponseEntity.ok(updated);
    } catch (BackendServiceException e) {
      log.error(
          "Update payout paid-out flag failed with status {}: {}",
          e.getStatusCode(),
          e.getMessage());
      if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      if (e.getStatusCode() == 404) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
      }
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    } catch (Exception e) {
      log.error("Update payout paid-out flag failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
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
