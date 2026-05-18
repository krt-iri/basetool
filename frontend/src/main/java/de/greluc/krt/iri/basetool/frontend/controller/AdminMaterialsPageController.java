package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialCategoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialUpdateAjaxRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the admin material catalog ({@code /admin/materials}).
 *
 * <p>The materials list is the heaviest reference-data table in the app — every UEX commodity plus
 * the project-specific job-order materials. The page renders the list once (sorted
 * case-insensitively) and uses a dedicated AJAX endpoint for the field-by-field admin edits so a
 * single category re-assignment doesn't reload the whole table. Category create/delete still goes
 * through full-page redirects because both invalidate the materials cache.
 */
@Controller
@RequestMapping("/admin/materials")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaterialsPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Loads the full materials list (size=1000, sorted by name asc) plus the category dropdown
   * source. The "refined materials" model attribute is the same list sorted again
   * case-insensitively — admins assign raw materials to a refined one even when the UEX flag is
   * wrong, so the dropdown intentionally includes every material rather than filtering by {@code
   * isRefined}.
   *
   * @param model Thymeleaf model populated with materials, refined-materials and categories
   * @return the {@code admin/materials} view name
   */
  @GetMapping
  public String listMaterials(Model model) {
    try {
      PageResponse<MaterialDto> materialsPage =
          backendApiClient.get(
              "/api/v1/materials?size=1000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<MaterialDto>>() {});

      List<MaterialDto> materials = new ArrayList<>();
      if (materialsPage != null && materialsPage.content() != null) {
        materials = new ArrayList<>(materialsPage.content());
      }

      // Provide a list of all materials for assignment to RAW materials
      // (bypass UEX data errors where refined materials are not marked correctly)
      List<MaterialDto> refinedMaterials =
          materials.stream()
              .sorted(
                  Comparator.comparing(
                      m -> m.name() == null ? "" : m.name(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      model.addAttribute("refinedMaterials", refinedMaterials);

      List<MaterialDto> sortedMaterials =
          materials.stream()
              .sorted(
                  Comparator.comparing(
                      m -> m.name() == null ? "" : m.name(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      model.addAttribute("materials", sortedMaterials);

      List<MaterialCategoryDto> categories =
          backendApiClient.get(
              "/api/v1/material-categories",
              new ParameterizedTypeReference<List<MaterialCategoryDto>>() {});
      model.addAttribute("categories", categories);

    } catch (Exception e) {
      log.error("Error loading materials data", e);
      model.addAttribute("error", "error.admin.materials.load");
    }
    return "admin/materials";
  }

  /**
   * Creates a new material category. The backend returns the created record; the page only needs to
   * flash a success/error toast and redirect to refresh the dropdown.
   *
   * @param name category name (must be unique across categories — backend enforces)
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/materials}
   */
  @PostMapping("/categories")
  public String createCategory(@RequestParam String name, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.post(
          "/api/v1/material-categories",
          new MaterialCategoryDto(null, name, null),
          MaterialCategoryDto.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Create category failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "notification.error.save");
    }
    return "redirect:/admin/materials";
  }

  /**
   * Deletes a material category. The backend's referential-integrity check refuses the call when
   * any material still references the category — the resulting 409 surfaces as a generic delete
   * error toast.
   *
   * @param id category id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /admin/materials}
   */
  @PostMapping("/categories/{id}/delete")
  public String deleteCategory(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/material-categories/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete category failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "notification.error.delete");
    }
    return "redirect:/admin/materials";
  }

  /**
   * AJAX endpoint that edits a single field on a material in place. The request's {@code
   * updateType} discriminator selects which field is being touched ({@code CATEGORY}, {@code
   * REFINED}, {@code QUANTITY_TYPE}, {@code MANUAL_RAW}, {@code JOB_ORDER}); every other field on
   * the material is preserved from the freshly-fetched current record. A {@code MANUAL_RAW} or
   * {@code JOB_ORDER} change additionally clears the frontend's static-data cache so dependent
   * pages see the new flag without a full reload. Failures collapse to a generic 500 — the AJAX
   * layer in the template renders a toast instead of relying on per-status semantics.
   *
   * @param id material id
   * @param request AJAX patch payload
   * @return the freshly re-fetched material on success, 400 on unknown update type, 500 on backend
   *     failure
   */
  @ResponseBody
  @PutMapping("/{id}/ajax")
  public ResponseEntity<MaterialDto> updateMaterialAjax(
      @PathVariable @NotNull UUID id, @Valid @RequestBody MaterialUpdateAjaxRequest request) {
    try {
      MaterialDto currentMaterial =
          backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);

      MaterialCategoryDto category = currentMaterial.category();
      MaterialDto refinedMaterial = currentMaterial.refinedMaterial();
      String quantityType = currentMaterial.quantityType();
      Boolean isManualRawMaterial = currentMaterial.isManualRawMaterial();
      Boolean isJobOrder = currentMaterial.isJobOrder();

      if ("CATEGORY".equals(request.updateType())) {
        if (request.categoryId() != null) {
          category =
              backendApiClient.get(
                  "/api/v1/material-categories/" + request.categoryId(), MaterialCategoryDto.class);
        } else {
          category = null;
        }
      } else if ("REFINED".equals(request.updateType())) {
        if (request.refinedMaterialId() != null) {
          refinedMaterial =
              backendApiClient.get(
                  "/api/v1/materials/" + request.refinedMaterialId(), MaterialDto.class);
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

      MaterialDto body =
          new MaterialDto(
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
              request.version());

      backendApiClient.put("/api/v1/materials/" + id, body, Void.class);
      if ("JOB_ORDER".equals(request.updateType()) || "MANUAL_RAW".equals(request.updateType())) {
        backendApiClient.clearStaticDataCache();
      }
      MaterialDto updatedMaterial =
          backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
      return ResponseEntity.ok(updatedMaterial);
    } catch (Exception e) {
      log.error("Ajax update material failed", e);
      // In case of OptimisticLocking, backend usually returns 409 Conflict
      return ResponseEntity.status(500).build();
    }
  }
}
