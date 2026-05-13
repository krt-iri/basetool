package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.form.OperationForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/operations")
@RequiredArgsConstructor
@Slf4j
public class OperationPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String listOperations(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            Model model) {
        try {
            PageResponse<OperationDto> operationsPage = backendApiClient.get(
                    "/api/v1/operations?page=" + page + "&size=" + size + "&sort=createdAt,desc",
                    new ParameterizedTypeReference<PageResponse<OperationDto>>() {},
                    false
            );
            model.addAttribute("operations", operationsPage.content());
            model.addAttribute("operationsPage", operationsPage);
        } catch (Exception e) {
            log.error("Error loading operations", e);
            model.addAttribute("error", "error.operations.load");
        }
        return "operations-index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String operationDetails(
            @PathVariable @NotNull UUID id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            Authentication authentication,
            Model model) {
        try {
            OperationDto operation = backendApiClient.get("/api/v1/operations/" + id, OperationDto.class, false);
            model.addAttribute("operation", operation);

            PageResponse<MissionListDto> missionsPage = backendApiClient.get(
                    "/api/v1/missions/search?operationId=" + id + "&page=" + page + "&size=" + size + "&sort=plannedStartTime,asc",
                    new ParameterizedTypeReference<PageResponse<MissionListDto>>() {},
                    false
            );
            model.addAttribute("missions", missionsPage.content());
            model.addAttribute("missionsPage", missionsPage);

            OperationFinanceDto operationFinance = backendApiClient.get("/api/v1/operations/" + id + "/finances", OperationFinanceDto.class, false);
            model.addAttribute("operationFinance", operationFinance);

            List<OperationPayoutDto> payouts = backendApiClient.get(
                    "/api/v1/operations/" + id + "/payouts",
                    new ParameterizedTypeReference<List<OperationPayoutDto>>() {},
                    false
            );
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
                .anyMatch(role -> "ROLE_ADMIN".equals(role)
                        || "ROLE_OFFICER".equals(role)
                        || "ROLE_MISSION_MANAGER".equals(role));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public String createOperation(
            @ModelAttribute OperationForm form,
            RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.post("/api/v1/operations", form, Void.class);
            redirectAttributes.addFlashAttribute("successMessage", "operation.create.success");
        } catch (Exception e) {
            log.error("Error creating operation", e);
            redirectAttributes.addFlashAttribute("errorMessage", "operation.create.error");
        }
        return "redirect:/operations";
    }

    @PostMapping("/{id}/update")
    @PreAuthorize("hasRole('MISSION_MANAGER')")
    public String updateOperation(
            @PathVariable @NotNull UUID id,
            @ModelAttribute OperationForm form,
            RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.put("/api/v1/operations/" + id, form, Void.class);
            redirectAttributes.addFlashAttribute("successMessage", "operation.update.success");
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Conflict e) {
            log.warn("Optimistic locking failure updating operation: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "error.optimistic.locking");
        } catch (Exception e) {
            log.error("Error updating operation", e);
            redirectAttributes.addFlashAttribute("errorMessage", "operation.update.error");
        }
        return "redirect:/operations";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOperation(
            @PathVariable @NotNull UUID id,
            RedirectAttributes redirectAttributes) {
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
