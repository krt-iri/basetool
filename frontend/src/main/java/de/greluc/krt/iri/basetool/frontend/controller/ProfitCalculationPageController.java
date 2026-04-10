package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.ShipTypeDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/materials/profit-calculation")
@RequiredArgsConstructor
public class ProfitCalculationPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping
    @PreAuthorize("hasAnyRole('SQUADRON_MEMBER', 'MEMBER', 'OFFICER', 'ADMIN')")
    public String showProfitCalculationPage(Model model) {
        log.debug("Showing profit calculation page");
        try {
            // Fetch ship types for the dropdown
            PageResponse<ShipTypeDto> shipTypesPage = backendApiClient.get(
                    "/api/v1/ship-types?size=1000&sort=name,asc",
                    new ParameterizedTypeReference<PageResponse<ShipTypeDto>>() {}
            );
            
            List<ShipTypeDto> shipTypes = (shipTypesPage != null && shipTypesPage.content() != null) 
                    ? shipTypesPage.content().stream()
                        .filter(s -> s.scu() != null && s.scu() > 0)
                        .toList()
                    : List.of();
            
            model.addAttribute("shipTypes", shipTypes);

            // Set C2 as default ship if present
            shipTypes.stream()
                    .filter(s -> s.name().contains("C2 Hercules Starlifter"))
                    .findFirst()
                    .ifPresent(c2 -> model.addAttribute("defaultShipId", c2.id()));

            // Fetch terminals to get unique star systems
            PageResponse<Map<String, Object>> terminalsPage = backendApiClient.get(
                    "/api/v1/terminals?size=10000",
                    new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {}
            );

            Set<String> starSystems = new TreeSet<>();
            if (terminalsPage != null && terminalsPage.content() != null) {
                for (Map<String, Object> terminal : terminalsPage.content()) {
                    Object system = terminal.get("starSystemName");
                    if (system != null && !system.toString().isBlank()) {
                        starSystems.add(system.toString());
                    }
                }
            }
            model.addAttribute("starSystems", starSystems);

        } catch (Exception e) {
            log.error("Error loading data for profit calculation page", e);
            model.addAttribute("error", "error.profit_calculation.load");
        }
        
        return "materials-profit-calculation";
    }
}
