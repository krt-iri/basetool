package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ManufacturerDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.SquadronShipOverviewDto;
import de.greluc.krt.iri.basetool.frontend.model.form.ShipForm;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/hangar")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class HangarPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    public String viewHangar(Model model) {
        if (!model.containsAttribute("shipForm")) {
            model.addAttribute("shipForm", new ShipForm());
        }
        
        List<ShipDto> myShips = new ArrayList<>();
        try {
            PageResponse<ShipDto> p = backendApiClient.get("/api/v1/hangar/my-ships?size=1000", new ParameterizedTypeReference<>() {});
            if (p != null && p.content() != null) {
                myShips = new ArrayList<>(p.content());
                myShips.sort(Comparator
                    .comparing((ShipDto s) -> s.shipType() != null && s.shipType().manufacturer() != null ? s.shipType().manufacturer().name() : "", String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(s -> s.shipType() != null ? s.shipType().name() : "", String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((ShipDto s) -> {
                        String ins = s.insurance();
                        if (ins == null || ins.equals("0")) return 3;
                        if (ins.equals("LTI")) return 1;
                        return 2;
                    })
                    .thenComparing((ShipDto s) -> {
                        String ins = s.insurance();
                        if (ins == null || ins.equals("LTI") || ins.equals("0")) return 0;
                        try {
                            return Integer.parseInt(ins);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }, Comparator.reverseOrder())
                    .thenComparing(s -> s.location() != null ? s.location().name() : "", String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(s -> (s.fitted() != null && s.fitted()) ? 0 : 1)
                    .thenComparing(s -> s.name() != null ? s.name() : "", String.CASE_INSENSITIVE_ORDER)
                );
            }
        } catch (Exception e) {
            log.error("Failed to fetch my ships", e);
            log.error("Error loading ships", e);
            model.addAttribute("error", "error.hangar.ships.load");
        }
        
        List<ShipTypeDto> shipTypes = new ArrayList<>();
        try {
            PageResponse<ShipTypeDto> p = backendApiClient.getCached("/api/v1/ship-types?size=1000", new ParameterizedTypeReference<>() {});
            if (p != null && p.content() != null) {
                shipTypes = new ArrayList<>(p.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch ship types", e);
        }
        shipTypes.sort(Comparator.comparing(ShipTypeDto::name, String.CASE_INSENSITIVE_ORDER));
        
        List<LocationDto> locations = new ArrayList<>();
        try {
            PageResponse<LocationDto> pLoc = backendApiClient.getCached("/api/v1/locations?size=1000", new ParameterizedTypeReference<>() {});
            if (pLoc != null && pLoc.content() != null) {
                locations = new ArrayList<>(pLoc.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch locations", e);
        }
        locations.sort(Comparator.comparing(LocationDto::name, String.CASE_INSENSITIVE_ORDER));

        List<ManufacturerDto> manufacturers = new ArrayList<>();
        try {
            PageResponse<ManufacturerDto> pMan = backendApiClient.getCached("/api/v1/manufacturers?size=1000", new ParameterizedTypeReference<>() {});
            if (pMan != null && pMan.content() != null) {
                manufacturers = new ArrayList<>(pMan.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch manufacturers", e);
        }
        manufacturers.sort(Comparator.comparing(ManufacturerDto::name, String.CASE_INSENSITIVE_ORDER));
        
        model.addAttribute("myShips", myShips);
        model.addAttribute("shipTypes", shipTypes);
        model.addAttribute("locations", locations);
        model.addAttribute("manufacturers", manufacturers);
        
        return "hangar";
    }

    @GetMapping("/squadron")
    public String viewSquadron(Model model) {
        List<SquadronShipOverviewDto> overview = new ArrayList<>();
        try {
            PageResponse<SquadronShipOverviewDto> res = backendApiClient.get("/api/v1/hangar/squadron-overview?size=1000", new ParameterizedTypeReference<>() {});
            if (res != null && res.content() != null) {
                overview = new ArrayList<>(res.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch squadron overview", e);
            model.addAttribute("error", "error.hangar.squadron.load");
        }

        // Only show types where count is at least 1 (the backend query groups by existing ships, so count is always >= 1 anyway)
        // Sort by ship type name
        overview.sort(Comparator.comparing(dto -> dto.shipType().name(), String.CASE_INSENSITIVE_ORDER));
        
        model.addAttribute("overview", overview);
        return "hangar-squadron";
    }

    @PostMapping("/add")
    public String addShip(@Valid @ModelAttribute("shipForm") ShipForm form,
                          BindingResult bindingResult,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Render directly; the BindingResult stays request-scoped so it never goes
            // through a Redis-serialised FlashMap (see RedisSessionConfig).
            model.addAttribute("showShipModal", true);
            model.addAttribute("modalAction", "/hangar/add");
            return viewHangar(model);
        }

        try {
            ShipRequestDto request = new ShipRequestDto(
                form.getName(),
                form.getShipTypeId(),
                form.getInsurance(),
                form.getLocationId(),
                form.isFitted(),
                null
            );
            backendApiClient.post("/api/v1/hangar/ships", request, ShipDto.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_add");
        } catch (Exception e) {
            log.error("Failed to add ship", e);
            log.error("Error adding ship", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.add");
            redirectAttributes.addFlashAttribute("shipForm", form);
        }
        return "redirect:/hangar";
    }

    @PostMapping("/{id}/update")
    public String updateShip(@PathVariable @NotNull UUID id,
                             @Valid @ModelAttribute("shipForm") ShipForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.warn("Validation failed for ship update {}", id);
            model.addAttribute("errorToast", "error.validation.failed");
            model.addAttribute("showShipModal", true);
            model.addAttribute("modalAction", "/hangar/" + id + "/update");
            return viewHangar(model);
        }

        try {
            ShipRequestDto request = new ShipRequestDto(
                form.getName(),
                form.getShipTypeId(),
                form.getInsurance(),
                form.getLocationId(),
                form.isFitted(),
                form.getVersion()
            );
            backendApiClient.put("/api/v1/hangar/ships/" + id, request, ShipDto.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_update");
        } catch (Exception e) {
            log.error("Failed to update ship", e);
            log.error("Error updating ship", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.update");
        }
        return "redirect:/hangar";
    }

    @PostMapping("/{id}/delete")
    public String deleteShip(@PathVariable @NotNull UUID id, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/hangar/ships/" + id, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "notification.success.ship_delete");
        } catch (Exception e) {
            log.error("Failed to delete ship", e);
            log.error("Error deleting ship", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.hangar.ship.delete");
        }
        return "redirect:/hangar";
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) {
                return ((Number) o).longValue();
            }
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String parseString(Object o) {
        return o == null ? null : o.toString();
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
