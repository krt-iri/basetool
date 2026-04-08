package de.greluc.krt.iri.basetool.frontend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProxyController {

    private final BackendApiClient backendApiClient;

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> searchUsers(@RequestParam String query) {
        PageResponse<Map<String, Object>> response = backendApiClient.get(
                "/api/v1/users/search?query=" + query + "&size=1000&sort=username,asc",
                new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {}
        );
        return response != null && response.content() != null ? response.content() : List.of();
    }
}
