package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.ProfitCalculationDto;
import de.greluc.krt.iri.basetool.backend.service.ProfitCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/materials/profit-calculation")
@RequiredArgsConstructor
public class ProfitCalculationController {

    private final ProfitCalculationService profitCalculationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SQUADRON_MEMBER', 'MEMBER', 'OFFICER', 'ADMIN')")
    public List<ProfitCalculationDto> getProfitCalculation(
            @RequestParam UUID shipId,
            @RequestParam(required = false) List<String> starSystemNames) {
        log.debug("GET profit calculation for shipId: {} and starSystems: {}", shipId, starSystemNames);
        return profitCalculationService.calculateProfit(shipId, starSystemNames);
    }
}
