package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.form.OperationForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
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
            PageResponse<Map<String, Object>> operationsPage = backendApiClient.get(
                    "/api/v1/operations?page=" + page + "&size=" + size + "&sort=createdAt,desc",
                    new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
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
            Model model) {
        try {
            Map<String, Object> operation = backendApiClient.get("/api/v1/operations/" + id, new ParameterizedTypeReference<Map<String, Object>>() {}, false);
            model.addAttribute("operation", operation);

            // Fetch missions for this operation
            PageResponse<Map<String, Object>> missionsPage = backendApiClient.get(
                    "/api/v1/missions/search?operationId=" + id + "&page=" + page + "&size=" + size + "&sort=plannedStartTime,asc",
                    new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {},
                    false
            );
            model.addAttribute("missions", missionsPage.content());
            model.addAttribute("missionsPage", missionsPage);

            OperationFinanceDto operationFinance = backendApiClient.get("/api/v1/operations/" + id + "/finances", OperationFinanceDto.class, false);
            model.addAttribute("operationFinance", operationFinance);

            java.util.List<de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutDto> payouts = backendApiClient.get(
                    "/api/v1/operations/" + id + "/payouts",
                    new ParameterizedTypeReference<java.util.List<de.greluc.krt.iri.basetool.frontend.model.dto.OperationPayoutDto>>() {},
                    false
            );
            model.addAttribute("operationPayouts", payouts);

        } catch (Exception e) {
            log.error("Error loading operation details", e);
            model.addAttribute("error", "error.operation.load");
            return "redirect:/operations";
        }
        return "operation-detail";
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