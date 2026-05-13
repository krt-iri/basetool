package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialMatrixItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialPriceOverviewDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/materials")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class MaterialsPageController {

  public record TerminalCol(
      String name,
      String nickname,
      String starSystemName,
      String cityName,
      String spaceStationName,
      String outpostName,
      Boolean isJumpPoint,
      Boolean hasLoadingDock,
      Boolean isAutoLoad)
      implements Comparable<TerminalCol> {

    private int getGroupPriority() {
      if (cityName != null && !cityName.isBlank()) {
        return 1;
      }
      if (spaceStationName != null && !spaceStationName.isBlank()) {
        if (Boolean.TRUE.equals(isJumpPoint)) {
          return 2;
        }
        if (Boolean.TRUE.equals(hasLoadingDock)) {
          return 3;
        }
        return 4;
      }
      if (outpostName != null && !outpostName.isBlank()) {
        return 5;
      }
      return 6;
    }

    @Override
    public int compareTo(TerminalCol o) {
      String thisSystem = this.starSystemName != null ? this.starSystemName : "";
      String otherSystem = o.starSystemName != null ? o.starSystemName : "";
      int sysCmp = thisSystem.compareToIgnoreCase(otherSystem);
      if (sysCmp != 0) {
        return sysCmp;
      }

      int group1 = this.getGroupPriority();
      int group2 = o.getGroupPriority();
      if (group1 != group2) {
        return Integer.compare(group1, group2);
      }

      String thisName = this.name != null ? this.name : "";
      String otherName = o.name != null ? o.name : "";
      return thisName.compareToIgnoreCase(otherName);
    }
  }

  public record SystemGroup(String name, int count) {}

  public record MaterialRow(
      String materialName,
      Boolean isIllegal,
      Boolean isVolatileQt,
      Boolean isVolatileTime,
      Map<String, MaterialMatrixItemDto> prices)
      implements Comparable<MaterialRow> {
    @Override
    public int compareTo(MaterialRow o) {
      return this.materialName.compareToIgnoreCase(o.materialName);
    }
  }

  private final BackendApiClient backendApiClient;

  @GetMapping
  public String listMaterials(Model model) {
    try {
      PageResponse<MaterialPriceOverviewDto> page =
          backendApiClient.get(
              "/api/v1/materials/prices-overview?size=10000&sort=name,asc",
              new ParameterizedTypeReference<PageResponse<MaterialPriceOverviewDto>>() {});

      List<MaterialPriceOverviewDto> materials = new ArrayList<>();
      if (page != null && page.content() != null) {
        materials = new ArrayList<>(page.content());
      }

      Map<String, List<MaterialPriceOverviewDto>> materialsByKind = new TreeMap<>();
      for (MaterialPriceOverviewDto mat : materials) {
        String kind =
            mat.category() != null
                    && mat.category().name() != null
                    && !mat.category().name().isBlank()
                ? mat.category().name()
                : "Unsortiert";
        materialsByKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(mat);
      }
      // Sort items within each kind alphabetically by name (already sorted from API, but just to be
      // sure)
      materialsByKind
          .values()
          .forEach(
              list ->
                  list.sort(
                      Comparator.comparing(
                          MaterialPriceOverviewDto::name, String.CASE_INSENSITIVE_ORDER)));

      model.addAttribute("materials", materials);
      model.addAttribute("materialsByKind", materialsByKind);
    } catch (Exception e) {
      log.error("Error loading materials overview", e);
      model.addAttribute("error", "error.materials.load");
      model.addAttribute("materials", new ArrayList<>());
      model.addAttribute("materialsByKind", new TreeMap<>());
    }

    return "materials";
  }

  @GetMapping("/overview")
  public String getMatrixOverview(
      @RequestParam(required = false) List<String> systems,
      @RequestParam(required = false) List<String> materials,
      @RequestParam(defaultValue = "false") boolean filterLoadingDock,
      @RequestParam(defaultValue = "false") boolean filterAutoLoad,
      @RequestParam(defaultValue = "false") boolean fragment,
      Model model) {
    try {
      PageResponse<MaterialMatrixItemDto> page =
          backendApiClient.get(
              "/api/v1/materials/matrix?size=100000",
              new ParameterizedTypeReference<PageResponse<MaterialMatrixItemDto>>() {});

      List<MaterialMatrixItemDto> items = new ArrayList<>();
      if (page != null && page.content() != null) {
        items = new ArrayList<>(page.content());
      }

      // Unique systems for filter
      Set<String> starSystems =
          items.stream()
              .map(item -> item.starSystemName() != null ? item.starSystemName() : "")
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toCollection(TreeSet::new));
      model.addAttribute("starSystems", starSystems);

      // Unique materials for filter
      Set<String> materialNames =
          items.stream()
              .map(MaterialMatrixItemDto::materialName)
              .collect(Collectors.toCollection(TreeSet::new));
      model.addAttribute("materialNames", materialNames);

      Set<TerminalCol> terminals = new TreeSet<>();
      Map<String, Map<String, MaterialMatrixItemDto>> materialPrices = new TreeMap<>();
      Map<String, String> kindByMaterial = new HashMap<>();

      Map<String, Boolean> isIllegalByMaterial = new HashMap<>();
      Map<String, Boolean> isVolatileQtByMaterial = new HashMap<>();
      Map<String, Boolean> isVolatileTimeByMaterial = new HashMap<>();

      for (MaterialMatrixItemDto item : items) {
        if (systems != null && !systems.isEmpty() && !systems.contains(item.starSystemName())) {
          continue;
        }
        if (materials != null && !materials.isEmpty() && !materials.contains(item.materialName())) {
          continue;
        }
        if (filterLoadingDock && !item.hasLoadingDock()) {
          continue;
        }
        if (filterAutoLoad && !item.isAutoLoad()) {
          continue;
        }

        terminals.add(
            new TerminalCol(
                item.terminalName(),
                item.terminalNickname(),
                item.starSystemName() != null ? item.starSystemName() : "",
                item.cityName(),
                item.spaceStationName(),
                item.outpostName(),
                item.isJumpPoint(),
                item.hasLoadingDock(),
                item.isAutoLoad()));
        kindByMaterial.put(
            item.materialName(),
            item.category() != null
                    && item.category().name() != null
                    && !item.category().name().isBlank()
                ? item.category().name()
                : "Unsortiert");
        if (item.isIllegal() != null) {
          isIllegalByMaterial.put(item.materialName(), item.isIllegal());
        }
        if (item.isVolatileQt() != null) {
          isVolatileQtByMaterial.put(item.materialName(), item.isVolatileQt());
        }
        if (item.isVolatileTime() != null) {
          isVolatileTimeByMaterial.put(item.materialName(), item.isVolatileTime());
        }
        materialPrices
            .computeIfAbsent(item.materialName(), k -> new HashMap<>())
            .put(item.terminalName(), item);
      }

      Map<String, List<MaterialRow>> rowsByKind = new TreeMap<>();
      for (Map.Entry<String, Map<String, MaterialMatrixItemDto>> entry :
          materialPrices.entrySet()) {
        String matName = entry.getKey();
        String kind = kindByMaterial.get(matName);
        Boolean isIllegal = isIllegalByMaterial.getOrDefault(matName, false);
        Boolean isVolatileQt = isVolatileQtByMaterial.getOrDefault(matName, false);
        Boolean isVolatileTime = isVolatileTimeByMaterial.getOrDefault(matName, false);
        rowsByKind
            .computeIfAbsent(kind, k -> new ArrayList<>())
            .add(
                new MaterialRow(
                    matName, isIllegal, isVolatileQt, isVolatileTime, entry.getValue()));
      }
      rowsByKind
          .values()
          .forEach(
              list ->
                  list.sort(
                      Comparator.comparing(
                          MaterialRow::materialName, String.CASE_INSENSITIVE_ORDER)));

      List<SystemGroup> systemGroups = new ArrayList<>();
      String currentSystem = null;
      int currentCount = 0;
      for (TerminalCol term : terminals) {
        if (currentSystem == null) {
          currentSystem = term.starSystemName();
          currentCount = 1;
        } else if (currentSystem.equals(term.starSystemName())) {
          currentCount++;
        } else {
          systemGroups.add(new SystemGroup(currentSystem, currentCount));
          currentSystem = term.starSystemName();
          currentCount = 1;
        }
      }
      if (currentSystem != null) {
        systemGroups.add(new SystemGroup(currentSystem, currentCount));
      }

      model.addAttribute("terminals", terminals);
      model.addAttribute("systemGroups", systemGroups);
      model.addAttribute("rowsByKind", rowsByKind);

    } catch (Exception e) {
      log.error("Error loading materials matrix", e);
      model.addAttribute("error", "error.materials.matrix.load");
      model.addAttribute("terminals", new TreeSet<>());
      model.addAttribute("rowsByKind", new TreeMap<>());
      model.addAttribute("starSystems", new TreeSet<>());
      model.addAttribute("materialNames", new TreeSet<>());
    }

    if (fragment) {
      return "materials-overview :: matrixTableFragment";
    }
    return "materials-overview";
  }

  @GetMapping("/{id}")
  public String getMaterialDetail(@PathVariable @NotNull UUID id, Model model) {
    try {
      MaterialDto material = backendApiClient.get("/api/v1/materials/" + id, MaterialDto.class);
      model.addAttribute("material", material);

      PageResponse<MaterialPriceDto> pricesPage =
          backendApiClient.get(
              "/api/v1/materials/" + id + "/prices?size=1000&sort=terminal.name,asc",
              new ParameterizedTypeReference<PageResponse<MaterialPriceDto>>() {});

      List<MaterialPriceDto> prices = new ArrayList<>();
      if (pricesPage != null && pricesPage.content() != null) {
        prices = new ArrayList<>(pricesPage.content());
      }
      model.addAttribute("prices", prices);

    } catch (Exception e) {
      log.error("Error loading material detail for id {}", id, e);
      model.addAttribute("error", "error.material.details.load");
      model.addAttribute("material", null);
      model.addAttribute("prices", new ArrayList<>());
    }
    return "material-detail";
  }
}
