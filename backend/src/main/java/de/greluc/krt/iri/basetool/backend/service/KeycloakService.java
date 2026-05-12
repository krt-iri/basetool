package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.KeycloakSyncProperties;
import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

    private final KeycloakSyncProperties properties;

    public List<KeycloakUserDto> fetchUsers() {
        if (!properties.isEnabled() || properties.getAdminUrl() == null) {
            log.debug("Keycloak sync disabled or admin URL missing");
            return Collections.emptyList();
        }

        try {
            String token = getAccessToken();
            
            List<KeycloakUserDto> users = RestClient.builder()
                    .baseUrl(properties.getAdminUrl())
                    .build()
                    .get()
                    .uri("/admin/realms/{realm}/users", properties.getRealm())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<KeycloakUserDto>>() {});

            if (users == null) return Collections.emptyList();

            return users.stream()
                    .map(u -> {
                        Set<String> roles = fetchUserRoles(u.id(), token);
                        return new KeycloakUserDto(
                                u.id(),
                                u.username(),
                                u.firstName(),
                                u.lastName(),
                                u.email(),
                                u.enabled(),
                                roles
                        );
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Failed to fetch users from Keycloak", e);
            return Collections.emptyList();
        }
    }

    private Set<String> fetchUserRoles(UUID userId, String token) {
        try {
            List<Map<String, Object>> roles = RestClient.builder()
                    .baseUrl(properties.getAdminUrl())
                    .build()
                    .get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.getRealm(), userId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (roles == null) return Collections.emptySet();
            return roles.stream()
                    .map(r -> (String) r.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to fetch roles for user {}", userId, e);
            return Collections.emptySet();
        }
    }

    private String getAccessToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", properties.getClientId());
        formData.add("client_secret", properties.getClientSecret());

        try {
            Map response = RestClient.builder()
                    .baseUrl(properties.getAdminUrl())
                    .build()
                    .post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("access_token")) {
                return (String) response.get("access_token");
            }

            throw new de.greluc.krt.iri.basetool.backend.exception.ExternalServiceException(
                    "Could not retrieve access token from Keycloak. Response: " + response);
        } catch (RestClientResponseException e) {
            throw new de.greluc.krt.iri.basetool.backend.exception.ExternalServiceException(
                    "Keycloak returned error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        }
    }
}
