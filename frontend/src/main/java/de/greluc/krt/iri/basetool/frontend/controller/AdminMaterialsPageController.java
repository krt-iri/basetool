package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialCategoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
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

import java.util.*;

@Controller
@RequestMapping("/admin/materials")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
public class AdminMaterialsPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    public String listMaterials(Model model) {
        try {
            PageResponse<MaterialDto> materialsPage = backendApiClient.get(
                    "/api/v1/materials?size=1000&sort=name,asc",
                    new ParameterizedTypeReference<PageResponse<MaterialDto>>() {}
            );

            List<MaterialDto> materials = new ArrayList<>();
            if (materialsPage != null && materialsPage.content() != null) {
                materials = new ArrayList<>(materialsPage.content());
            }

            // Provide a list of only REFINED materials for assignment to RAW materials
            List<MaterialDto> refinedMaterials = materials.stream()
                .filter(m -> "REFINED".equals(m.type()))
                .sorted(Comparator.comparing(m -> m.name() == null ? "" : m.name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
            model.addAttribute("refinedMaterials", refinedMaterials);

            List<MaterialDto> sortedMaterials = materials.stream()
                .sorted(Comparator.comparing(m -> m.name() == null ? "" : m.name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
            model.addAttribute("materials", sortedMaterials);

            List<MaterialCategoryDto> categories = backendApiClient.get(
                    "/api/v1/material-categories",
                    new ParameterizedTypeReference<List<MaterialCategoryDto>>() {}
            );
            model.addAttribute("categories", categories);

        } catch (Exception e) {
            log.error("Error loading materials data", e);
            model.addAttribute("error", "error.admin.materials.load");
        }
        return "admin/materials";
    }

    @PostMapping("/categories")
    public String createCategory(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.post("/api/v1/material-categories", new MaterialCategoryDto(null, name, null), MaterialCategoryDto.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Create category failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "Error creating category");
        }
        return "redirect:/admin/materials";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/material-categories/" + id, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
        } catch (Exception e) {
            log.error("Delete category failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "Error deleting category");
        }
        return "redirect:/admin/materials";
    }

    @PostMapping("/{id}/category")
    public String updateMaterialCategory(@PathVariable @NotNull UUID id, @RequestParam(required = false) UUID categoryId, RedirectAttributes redirectAttributes) {
        try {
            MaterialDto currentMaterial = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
            MaterialCategoryDto category = null;
            if (categoryId != null) {
                category = backendApiClient.get("/api/v1/material-categories/" + categoryId, MaterialCategoryDto.class);
            }
            MaterialDto body = new MaterialDto(id, currentMaterial.idCommodity(), currentMaterial.name(), currentMaterial.type(), currentMaterial.quantityType(), currentMaterial.description(), currentMaterial.refinedMaterial(), category, currentMaterial.isIllegal(), currentMaterial.isVolatileQt(), currentMaterial.isVolatileTime(), currentMaterial.version());
            backendApiClient.put("/api/v1/materials/" + id, body, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Update material category failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "Update failed");
        }
        return "redirect:/admin/materials";
    }

    @PostMapping("/{id}/refined")
    public String updateRefinedMaterial(@PathVariable @NotNull UUID id, @RequestParam(required = false) UUID refinedMaterialId, RedirectAttributes redirectAttributes) {
        try {
            MaterialDto currentMaterial = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
            MaterialDto refinedMaterial = null;
            if (refinedMaterialId != null) {
                refinedMaterial = backendApiClient.get("/api/v1/materials/" + refinedMaterialId, MaterialDto.class);
            }
            MaterialDto body = new MaterialDto(id, currentMaterial.idCommodity(), currentMaterial.name(), currentMaterial.type(), currentMaterial.quantityType(), currentMaterial.description(), refinedMaterial, currentMaterial.category(), currentMaterial.isIllegal(), currentMaterial.isVolatileQt(), currentMaterial.isVolatileTime(), currentMaterial.version());
            backendApiClient.put("/api/v1/materials/" + id, body, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Update refined material failed", e);
            return "redirect:/admin/materials?error=UpdateFailed";
        }
        return "redirect:/admin/materials";
    }

    @PostMapping("/{id}/quantity-type")
    public String updateQuantityType(@PathVariable @NotNull UUID id, @RequestParam String quantityType, RedirectAttributes redirectAttributes) {
        try {
            MaterialDto currentMaterial = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
            MaterialDto body = new MaterialDto(id, currentMaterial.idCommodity(), currentMaterial.name(), currentMaterial.type(), quantityType, currentMaterial.description(), currentMaterial.refinedMaterial(), currentMaterial.category(), currentMaterial.isIllegal(), currentMaterial.isVolatileQt(), currentMaterial.isVolatileTime(), currentMaterial.version());
            backendApiClient.put("/api/v1/materials/" + id, body, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
        } catch (Exception e) {
            log.error("Update quantity type failed", e);
            redirectAttributes.addFlashAttribute("errorToast", "Update failed");
        }
        return "redirect:/admin/materials";
    }
}
