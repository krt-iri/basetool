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
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialUpdateAjaxRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

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

            // Provide a list of all materials for assignment to RAW materials
            // (bypass UEX data errors where refined materials are not marked correctly)
            List<MaterialDto> refinedMaterials = materials.stream()
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

    @ResponseBody
    @PutMapping("/{id}/ajax")
    public ResponseEntity<MaterialDto> updateMaterialAjax(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody MaterialUpdateAjaxRequest request) {
        try {
            MaterialDto currentMaterial = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
            
            MaterialCategoryDto category = currentMaterial.category();
            MaterialDto refinedMaterial = currentMaterial.refinedMaterial();
            String quantityType = currentMaterial.quantityType();
            Boolean isManualRawMaterial = currentMaterial.isManualRawMaterial();
            Boolean isJobOrder = currentMaterial.isJobOrder();
            
            if ("CATEGORY".equals(request.updateType())) {
                if (request.categoryId() != null) {
                    category = backendApiClient.get("/api/v1/material-categories/" + request.categoryId(), MaterialCategoryDto.class);
                } else {
                    category = null;
                }
            } else if ("REFINED".equals(request.updateType())) {
                if (request.refinedMaterialId() != null) {
                    refinedMaterial = backendApiClient.get("/api/v1/materials/" + request.refinedMaterialId(), MaterialDto.class);
                } else {
                    refinedMaterial = null;
                }
            } else if ("QUANTITY_TYPE".equals(request.updateType())) {
                quantityType = request.quantityType();
            } else if ("MANUAL_RAW".equals(request.updateType())) {
                isManualRawMaterial = request.isManualRawMaterial();
            } else if ("JOB_ORDER".equals(request.updateType())) {
                isJobOrder = request.isJobOrder();
            } else {
                return ResponseEntity.badRequest().build();
            }
            
            MaterialDto body = new MaterialDto(
                id, 
                currentMaterial.idCommodity(), 
                currentMaterial.name(), 
                currentMaterial.type(), 
                quantityType, 
                currentMaterial.description(), 
                refinedMaterial, 
                category, 
                currentMaterial.isIllegal(), 
                currentMaterial.isVolatileQt(), 
                currentMaterial.isVolatileTime(), 
                isManualRawMaterial,
                isJobOrder,
                request.version()
            );
            
            backendApiClient.put("/api/v1/materials/" + id, body, Void.class);
            if ("JOB_ORDER".equals(request.updateType()) || "MANUAL_RAW".equals(request.updateType())) {
                backendApiClient.clearStaticDataCache();
            }
            MaterialDto updatedMaterial = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
            return ResponseEntity.ok(updatedMaterial);
        } catch (Exception e) {
            log.error("Ajax update material failed", e);
            // In case of OptimisticLocking, backend usually returns 409 Conflict
            return ResponseEntity.status(500).build();
        }
    }
}
