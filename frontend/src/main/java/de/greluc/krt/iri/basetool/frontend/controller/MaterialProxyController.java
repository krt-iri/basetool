package de.greluc.krt.iri.basetool.frontend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/proxy/materials")
@RequiredArgsConstructor
public class MaterialProxyController {

    private final BackendApiClient backendApiClient;

    @GetMapping("/{id}/terminals")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getMaterialTerminals(@PathVariable UUID id) {
        List<Map<String, Object>> response = backendApiClient.get(
                "/api/v1/materials/" + id + "/terminals",
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return response != null ? response : List.of();
    }

    @GetMapping("/profit-calculation")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getProfitCalculation(
            @RequestParam UUID shipId,
            @RequestParam(required = false) List<String> starSystemNames) {
        
        StringBuilder url = new StringBuilder("/api/v1/materials/profit-calculation?shipId=").append(shipId);
        if (starSystemNames != null && !starSystemNames.isEmpty()) {
            for (String system : starSystemNames) {
                url.append("&starSystemNames=").append(system);
            }
        }

        List<Map<String, Object>> response = backendApiClient.get(
                url.toString(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        return response != null ? response : List.of();
    }
}
