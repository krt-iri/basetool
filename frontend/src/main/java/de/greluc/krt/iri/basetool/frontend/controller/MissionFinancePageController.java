package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.form.MissionFinanceEntryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/missions/{id}/finance-entries")
@RequiredArgsConstructor
public class MissionFinancePageController {

    private final BackendApiClient backendApiClient;
    // The mission-detail view (and its model) lives in MissionPageController; we
    // delegate to it instead of forwarding/redirecting so a finance-form validation
    // error keeps the BindingResult request-scoped (no Redis FlashMap round-trip).
    // The injected reference is a Spring proxy, so @PreAuthorize on missionDetail is
    // still enforced.
    private final MissionPageController missionPageController;

    @PostMapping
    @PreAuthorize("permitAll()")
    public String addFinanceEntry(@PathVariable @NotNull UUID id,
                                  @Valid @ModelAttribute("financeForm") MissionFinanceEntryForm form,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes,
                                  @AuthenticationPrincipal OidcUser principal) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("openModal", "finance-entry-modal");
            return missionPageController.missionDetail(id, model, principal);
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("missionId", id);
            body.put("participantId", form.getParticipantId());
            body.put("note", form.getNote());
            body.put("type", form.getType());
            body.put("amount", form.getAmount());

            backendApiClient.post("/api/v1/finance-entries", body, Void.class, principal == null);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Add finance entry failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.finance.add");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{entryId}/update")
    @PreAuthorize("isAuthenticated()")
    public String updateFinanceEntry(@PathVariable @NotNull UUID id,
                                     @PathVariable @NotNull UUID entryId,
                                     @Valid @ModelAttribute("financeForm") MissionFinanceEntryForm form,
                                     BindingResult bindingResult,
                                     Model model,
                                     RedirectAttributes redirectAttributes,
                                     @AuthenticationPrincipal OidcUser principal) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("openModal", "edit-finance-entry-modal");
            model.addAttribute("modalAction", "/missions/" + id + "/finance-entries/" + entryId + "/update");
            return missionPageController.missionDetail(id, model, principal);
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("note", form.getNote());
            body.put("type", form.getType());
            body.put("amount", form.getAmount());
            body.put("version", form.getVersion());

            backendApiClient.put("/api/v1/finance-entries/" + entryId, body, Void.class, false);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Update finance entry failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.finance.update");
        }
        return "redirect:/missions/" + id;
    }

    @PostMapping("/{entryId}/delete")
    @PreAuthorize("isAuthenticated()")
    public String deleteFinanceEntry(@PathVariable @NotNull UUID id,
                                     @PathVariable @NotNull UUID entryId,
                                     @AuthenticationPrincipal OidcUser principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/finance-entries/" + entryId, Void.class, false);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
        } catch (Exception e) {
            log.error("Delete finance entry failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.finance.delete");
        }
        return "redirect:/missions/" + id;
    }
}