package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemCreateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemUpdateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.UexLocationDto;
import de.greluc.krt.iri.basetool.frontend.model.form.PersonalInventoryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Page controller backing the personal inventory user area. Renders the list view, the
 * create/edit modal (KRT-styled, no native confirm()) and proxies form submissions to the
 * backend via {@link BackendApiClient}.
 */
@Controller
@RequestMapping("/personal-inventory")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class PersonalInventoryPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    public String view(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       @RequestParam(required = false) String sort,
                       Model model) {
        if (!model.containsAttribute("personalInventoryForm")) {
            model.addAttribute("personalInventoryForm", new PersonalInventoryForm());
        }
        populateListing(model, q, page, size, sort);
        return "personal-inventory";
    }

    @PostMapping("/add")
    public String add(@Valid @ModelAttribute("personalInventoryForm") PersonalInventoryForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Render the list view directly instead of redirecting. We must NOT push the
            // BindingResult into a FlashAttribute: BeanPropertyBindingResult holds a back-
            // reference to its model map (which in turn re-contains the BindingResult), and
            // the GenericJacksonJsonRedisSerializer used for Spring Session blows up on the
            // self-referencing cycle with `Document nesting depth (501) exceeds the maximum
            // allowed (500)`. Spring already exposes both `personalInventoryForm` and the
            // associated BindingResult through @ModelAttribute, so the modal can re-display
            // the user's input and the field errors without any flash hand-off.
            model.addAttribute("showItemModal", true);
            model.addAttribute("modalAction", "/personal-inventory/add");
            populateListing(model, null, null, null, null);
            return "personal-inventory";
        }

        try {
            PersonalInventoryItemCreateRequest request = new PersonalInventoryItemCreateRequest(
                    form.getName(),
                    form.getNote(),
                    form.getLocationUexId(),
                    form.getLocationType(),
                    form.getQuantity()
            );
            backendApiClient.post("/api/v1/personal-inventory", request, PersonalInventoryItemDto.class);
            redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.created");
        } catch (Exception e) {
            log.error("Failed to create personal inventory item", e);
            redirectAttributes.addFlashAttribute("errorToast", "personalInventory.error.create");
            redirectAttributes.addFlashAttribute("personalInventoryForm", form);
        }
        return "redirect:/personal-inventory";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable @NotNull UUID id,
                         @Valid @ModelAttribute("personalInventoryForm") PersonalInventoryForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Same rationale as add(): render directly to avoid pushing the
            // self-referencing BindingResult through the Redis-backed FlashMap.
            model.addAttribute("showItemModal", true);
            model.addAttribute("modalAction", "/personal-inventory/" + id + "/update");
            populateListing(model, null, null, null, null);
            return "personal-inventory";
        }

        try {
            PersonalInventoryItemUpdateRequest request = new PersonalInventoryItemUpdateRequest(
                    form.getName(),
                    form.getNote(),
                    form.getLocationUexId(),
                    form.getLocationType(),
                    form.getQuantity(),
                    form.getVersion()
            );
            backendApiClient.put("/api/v1/personal-inventory/" + id, request, PersonalInventoryItemDto.class);
            redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.updated");
        } catch (Exception e) {
            log.error("Failed to update personal inventory item {}", id, e);
            redirectAttributes.addFlashAttribute("errorToast", classifyError(e, "personalInventory.error.update"));
        }
        return "redirect:/personal-inventory";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/personal-inventory/" + id, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.deleted");
        } catch (Exception e) {
            log.error("Failed to delete personal inventory item {}", id, e);
            redirectAttributes.addFlashAttribute("errorToast", classifyError(e, "personalInventory.error.delete"));
        }
        return "redirect:/personal-inventory";
    }

    /**
     * AJAX endpoint backing the KRT-styled UEX location typeahead. The frontend module
     * deliberately proxies through itself rather than letting the browser hit the backend
     * directly so that the same Spring Security session and CSRF policy apply.
     */
    @GetMapping("/uex-search")
    @ResponseBody
    public List<UexLocationDto> uexSearch(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Integer limit) {
        try {
            String query = q == null ? "" : q;
            int effectiveLimit = limit == null ? 25 : Math.min(2000, Math.max(1, limit));
            String uri = "/api/v1/uex/locations/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&limit=" + effectiveLimit;
            List<UexLocationDto> result = backendApiClient.get(uri, new ParameterizedTypeReference<>() {});
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            log.warn("UEX location typeahead failed for query='{}': {}", q, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Populates the model attributes that the {@code personal-inventory} template needs to
     * render the listing under the modal: the filter query echoed into the search input, the
     * fetched item list and the page metadata used by the pagination fragment. Used both by
     * the GET handler and by the POST handlers when re-rendering after a validation error.
     */
    private void populateListing(Model model, String q, Integer page, Integer size, String sort) {
        model.addAttribute("filterQuery", q == null ? "" : q);
        PageResponse<PersonalInventoryItemDto> items = fetchItems(q, page, size, sort);
        model.addAttribute("items", items != null ? items.content() : Collections.emptyList());
        model.addAttribute("page", items);
    }

    private PageResponse<PersonalInventoryItemDto> fetchItems(String q, Integer page, Integer size, String sort) {
        try {
            StringBuilder uri = new StringBuilder("/api/v1/personal-inventory?");
            if (page != null) uri.append("page=").append(page).append('&');
            uri.append("size=").append(size == null ? 50 : size);
            if (sort != null && !sort.isBlank()) {
                uri.append("&sort=").append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
            }
            if (q != null && !q.isBlank()) {
                uri.append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
            }
            return backendApiClient.get(uri.toString(), new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch personal inventory items", e);
            return new PageResponse<>(new ArrayList<>(), 0, size == null ? 50 : size, 0, 0, List.of());
        }
    }

    /**
     * Maps backend service exceptions into specific user-facing toast keys, falling back
     * to the supplied generic key for any error other than a 409 optimistic-locking
     * conflict (the only case worth distinguishing for the user).
     */
    private String classifyError(Exception e, String defaultKey) {
        if (e instanceof de.greluc.krt.iri.basetool.frontend.service.BackendServiceException bse
                && bse.getStatusCode() == 409) {
            return "personalInventory.error.conflict";
        }
        return defaultKey;
    }
}
