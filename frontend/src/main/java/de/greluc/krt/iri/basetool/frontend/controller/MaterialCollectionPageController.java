package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.MaterialCollectionEntryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserReferenceDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Frontend controller for the material collection overview page of a job order.
 * Loads inventory entries, users and locations from the backend and passes them to the template.
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class MaterialCollectionPageController {

    private final BackendApiClient backendApiClient;

    @GetMapping("/{jobOrderId}/material-collection")
    @PreAuthorize("isAuthenticated()")
    public String viewMaterialCollection(@PathVariable UUID jobOrderId, Model model) {
        List<MaterialCollectionEntryDto> entries = Collections.emptyList();
        List<UserReferenceDto> users = Collections.emptyList();
        List<LocationReferenceDto> locations = Collections.emptyList();

        try {
            entries = backendApiClient.get(
                    "/api/v1/orders/" + jobOrderId + "/material-collection",
                    new ParameterizedTypeReference<List<MaterialCollectionEntryDto>>() {});
        } catch (BackendServiceException e) {
            log.warn("Could not load material collection for job order {}: {}", jobOrderId, e.getMessage());
        }

        try {
            List<UserReferenceDto> rawUsers = backendApiClient.get("/api/v1/users/lookup", new ParameterizedTypeReference<>() {});
            users = rawUsers.stream()
                    .sorted(Comparator.comparing(
                            u -> (u.effectiveName() != null && !u.effectiveName().isBlank())
                                    ? u.effectiveName()
                                    : u.username(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (BackendServiceException e) {
            log.warn("Could not load users: {}", e.getMessage());
        }

        try {
            locations = backendApiClient.getCached("/api/v1/locations/lookup", new ParameterizedTypeReference<>() {});
        } catch (BackendServiceException e) {
            log.warn("Could not load locations: {}", e.getMessage());
        }

        model.addAttribute("jobOrderId", jobOrderId);
        model.addAttribute("entries", entries);
        model.addAttribute("users", users);
        model.addAttribute("locations", locations);
        return "material-collection";
    }
}
