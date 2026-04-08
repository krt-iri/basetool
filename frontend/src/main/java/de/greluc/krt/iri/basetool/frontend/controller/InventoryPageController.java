package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.form.InventoryForm;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class InventoryPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    public String viewAggregatedInventory(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          Model model) {
        List<AggregatedInventoryDto> aggregated = new ArrayList<>();
        try {
            StringBuilder uri = new StringBuilder("/api/v1/inventory/aggregated?");
            if (page != null) uri.append("page=").append(page).append("&");
            if (size != null) uri.append("size=").append(size).append("&");
            uri.append("sort=material.name,asc;quality,desc;amount,desc");

            PageResponse<AggregatedInventoryDto> p = backendApiClient.get(uri.toString(), new ParameterizedTypeReference<>() {});
            if (p != null) {
                if (p.content() != null) {
                    aggregated = new ArrayList<>(p.content());
                }
                model.addAttribute("inventoryPage", p);
            }
        } catch (Exception e) {
            log.error("Failed to fetch aggregated inventory", e);
            model.addAttribute("error", "error.inventory.aggregate.load");
        }
        
        List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> materials = fetchMaterials();
        
        model.addAttribute("aggregated", aggregated);
        model.addAttribute("materials", materials);
        return "inventory-index";
    }

    @GetMapping("/material/{materialId}")
    public String viewMaterialInventory(@PathVariable @NotNull UUID materialId, Model model) {
        List<InventoryItemDto> items = new ArrayList<>();
        try {
            PageResponse<InventoryItemDto> p = backendApiClient.get("/api/v1/inventory/material/" + materialId + "?size=1000", new ParameterizedTypeReference<>() {});
            if (p != null && p.content() != null) {
                items = new ArrayList<>(p.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch material inventory", e);
            model.addAttribute("error", "error.inventory.material.load");
        }
        
        model.addAttribute("items", items);
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("selectedMaterialId", materialId);
        model.addAttribute("jobOrders", fetchActiveJobOrders());
        return "inventory-material";
    }

    public record GroupedInventoryDto(
        de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto material,
        Double totalAmount,
        Double averageQuality,
        Integer maxQuality,
        List<InventoryItemDto> items
    ) {}

    @GetMapping("/my")
    public String viewMyInventory(Model model) {
        if (!model.containsAttribute("inventoryForm")) {
            model.addAttribute("inventoryForm", new InventoryForm());
        }
        if (!model.containsAttribute("inventoryBookOutForm")) {
            model.addAttribute("inventoryBookOutForm", new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm());
        }
        
        List<GroupedInventoryDto> groupedItems = new ArrayList<>();
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromPath("/api/v1/inventory/my-inventory/grouped").build().toUriString();
            List<GroupedInventoryDto> res = backendApiClient.get(url, new ParameterizedTypeReference<>() {});
            if (res != null) {
                groupedItems = res;
            }
        } catch (Exception e) {
            log.error("Failed to fetch my grouped inventory", e);
            model.addAttribute("error", "error.inventory.personal.load");
        }
        
        model.addAttribute("groupedItems", groupedItems);
        // keeping empty items list to not break any existing template iteration if any
        model.addAttribute("items", new ArrayList<>());
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("locations", fetchLocations());
        model.addAttribute("jobOrders", fetchActiveJobOrders());
        model.addAttribute("missions", fetchMissions());
        model.addAttribute("users", fetchUsers());
        return "inventory-my";
    }

    @GetMapping("/all")
    public String viewAllInventory(@RequestParam(required = false) List<UUID> materialIds, 
                                   @RequestParam(required = false) Integer minQuality, 
                                   @RequestParam(required = false, defaultValue = "false") boolean fragment,
                                   Model model) {
        if (!model.containsAttribute("inventoryForm")) {
            model.addAttribute("inventoryForm", new InventoryForm());
        }
        if (!model.containsAttribute("inventoryBookOutForm")) {
            model.addAttribute("inventoryBookOutForm", new de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm());
        }
        
        List<GroupedInventoryDto> groupedItems = new ArrayList<>();
        try {
            org.springframework.web.util.UriComponentsBuilder uriBuilder = org.springframework.web.util.UriComponentsBuilder.fromPath("/api/v1/inventory/all/grouped");
            if (materialIds != null && !materialIds.isEmpty()) {
                for (UUID id : materialIds) {
                    uriBuilder.queryParam("materialIds", id.toString());
                }
            }
            if (minQuality != null) {
                uriBuilder.queryParam("minQuality", minQuality);
            }
            String url = uriBuilder.build().toUriString();
            List<GroupedInventoryDto> res = backendApiClient.get(url, new ParameterizedTypeReference<>() {});
            if (res != null) {
                groupedItems = res;
            }
        } catch (Exception e) {
            log.error("Failed to fetch all grouped inventory", e);
            model.addAttribute("error", "error.inventory.global.load");
        }

        model.addAttribute("groupedItems", groupedItems);
        model.addAttribute("items", new ArrayList<>());
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("selectedMaterialIds", materialIds);
        model.addAttribute("selectedMinQuality", minQuality);
        model.addAttribute("locations", fetchLocations());
        model.addAttribute("jobOrders", fetchActiveJobOrders());
        model.addAttribute("missions", fetchMissions());
        model.addAttribute("users", fetchUsers());
        
        if (fragment) {
            return "inventory-admin :: inventoryTableFragment";
        }
        return "inventory-admin";
    }

    @GetMapping("/input")
    public String viewInputPage(@RequestParam(required = false) String source, Model model) {
        InventoryForm form;
        if (!model.containsAttribute("inventoryForm")) {
            form = new InventoryForm();
            if ("admin".equals(source)) {
                form.setIsGlobal(true);
            } else {
                form.setIsGlobal(false);
            }
            model.addAttribute("inventoryForm", form);
        } else {
            form = (InventoryForm) model.getAttribute("inventoryForm");
        }
        
        if (form != null && Boolean.TRUE.equals(form.getIsGlobal())) {
            model.addAttribute("users", fetchUsers());
        }
        
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("locations", fetchLocations());
        model.addAttribute("missions", fetchMissions());
        model.addAttribute("jobOrders", fetchActiveJobOrders());
        return "inventory-input";
    }

    @PostMapping("/input")
    public String addInventoryItem(@Valid @ModelAttribute("inventoryForm") InventoryForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (Boolean.TRUE.equals(form.getPersonal()) && (form.getJobOrderId() != null || form.getMissionId() != null)) {
            bindingResult.rejectValue("personal", "error.inventory.personal.assignment", "Ein persönlicher Eintrag darf keinem Auftrag oder Einsatz zugeordnet sein.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.inventoryForm", bindingResult);
            redirectAttributes.addFlashAttribute("inventoryForm", form);
            return "redirect:/inventory/input";
        }

        try {
            InventoryItemCreateDto request = new InventoryItemCreateDto(
                Boolean.TRUE.equals(form.getIsGlobal()) ? form.getUserId() : null,
                form.getMaterialId(),
                form.getLocationId(),
                form.getQuality(),
                form.getAmount(),
                form.getPersonal(),
                form.getMissionId(),
                form.getJobOrderId()
            );
            backendApiClient.post("/api/v1/inventory", request, InventoryItemDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.inventory.add");
        } catch (Exception e) {
            log.error("Failed to add inventory item", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.inventory.add.failed");
            redirectAttributes.addFlashAttribute("inventoryForm", form);
            return "redirect:/inventory/input";
        }
        // Redirect back to main page as required: "beim speichern wird er zurück auf die startseite der lagerverwaltung geführt."
        return "redirect:/inventory";
    }

    @PostMapping("/{id}/book-out")
    public String bookOutInventoryItem(@PathVariable @NotNull UUID id, @Valid @ModelAttribute("inventoryBookOutForm") de.greluc.krt.iri.basetool.frontend.model.form.InventoryBookOutForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes, @RequestHeader(value = "Referer", required = false) String referer) {
        String redirectPath = (referer != null && referer.contains("/inventory/all")) ? "/inventory/all" : "/inventory/my";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorToast", "error.validation.failed");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.inventoryBookOutForm", bindingResult);
            redirectAttributes.addFlashAttribute("inventoryBookOutForm", form);
            redirectAttributes.addFlashAttribute("showBookOutModal", id);
            return "redirect:" + redirectPath;
        }

        try {
            InventoryItemBookOutDto request = new InventoryItemBookOutDto(
                form.getAmount(),
                form.getTargetUserId(),
                form.getTargetLocationId(),
                form.getType(),
                form.getTerminal(),
                form.getSellAmount(),
                form.getVersion()
            );
            backendApiClient.post("/api/v1/inventory/" + id + "/book-out", request, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "success.inventory.bookout");
        } catch (Exception e) {
            log.error("Failed to book out inventory item", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.inventory.bookout.failed");
        }
        return "redirect:" + redirectPath;
    }

    private List<de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto> fetchUsers() {
        try {
            List<de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto> content = backendApiClient.get("/api/v1/users/lookup", new ParameterizedTypeReference<>() {});
            if (content != null) {
                return content;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch users (might not be an admin/officer)");
        }
        return new ArrayList<>();
    }

    private List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> fetchMaterials() {
        List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> materials = new ArrayList<>();
        try {
            List<de.greluc.krt.iri.basetool.frontend.model.dto.MaterialReferenceDto> content = backendApiClient.getCached("/api/v1/materials/lookup", new ParameterizedTypeReference<>() {});
            if (content != null) {
                materials.addAll(content);
            }
        } catch (Exception e) {
            log.error("Failed to fetch materials", e);
        }
        return materials;
    }

    private List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto> fetchLocations() {
        List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto> locations = new ArrayList<>();
        try {
            List<de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto> content = backendApiClient.getCached("/api/v1/locations/lookup", new ParameterizedTypeReference<>() {});
            if (content != null) {
                locations.addAll(content);
            }
        } catch (Exception e) {
            log.error("Failed to fetch locations", e);
        }
        return locations;
    }

    private List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto> fetchActiveJobOrders() {
        List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto> orders = new ArrayList<>();
        try {
            List<de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderReferenceDto> content = backendApiClient.get("/api/v1/orders/lookup", new ParameterizedTypeReference<>() {});
            if (content != null) {
                orders.addAll(content);
            }
        } catch (Exception e) {
            log.error("Failed to fetch active job orders", e);
        }
        return orders;
    }

    private List<de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto> fetchMissions() {
        try {
            List<de.greluc.krt.iri.basetool.frontend.model.dto.MissionReferenceDto> content = backendApiClient.get("/api/v1/missions/lookup", new ParameterizedTypeReference<>() {});
            if (content != null) {
                return content;
            }
        } catch (Exception e) {
            log.error("Failed to fetch missions", e);
        }
        return new ArrayList<>();
    }

    private String parseString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer parseInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private UUID parseUuid(Object o) {
        if (o == null) return null;
        try {
            return UUID.fromString(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
