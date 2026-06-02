package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.config.KeycloakSyncProperties;
import de.greluc.krt.iri.basetool.backend.model.dto.KeycloakUserDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Read-only client for the Keycloak Admin REST API used by the scheduled user sync.
 *
 * <p>Obtains an admin access token via the {@code client_credentials} grant against the realm's
 * {@code openid-connect/token} endpoint, then pages through {@code /admin/realms/{realm}/users} and
 * joins each user record with the user's realm role mappings. Failures are swallowed at the top
 * level: the scheduler treats an empty list as "skip this run" and never wipes local users based on
 * it (see {@link UserService#markMissingUsers}).
 *
 * <p>The {@link #BEARER_PREFIX} constant exists so the literal {@code "Bearer "} never gets typed
 * by hand at a new call site — open-coding the prefix in a future log statement is the canonical
 * way to accidentally leak a raw token; the PII masker catches it, but defense in depth is cheaper
 * than auditing every new log line.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

  /**
   * RFC 6750 Bearer-token authentication scheme prefix used when handing the access token to
   * Keycloak's admin REST API. Centralised here so the literal never gets typed by hand at a call
   * site — open-coding {@code "Bearer " + token} in a log statement or a future request builder is
   * the canonical way to accidentally leak a raw token into the logs (the PII masker catches it,
   * but defence in depth is cheaper than auditing every new log line).
   */
  private static final String BEARER_PREFIX = "Bearer ";

  private final KeycloakSyncProperties properties;

  /**
   * Fetches the entire realm user catalog plus role mappings.
   *
   * <p>Short-circuits to an empty list when sync is disabled or the admin URL is unconfigured — a
   * missing setting must NOT trigger an exception that would leak the configuration shape to the
   * sync task. Any unexpected error (network, auth, malformed payload) is logged and the method
   * returns an empty list; the scheduler then treats the run as "skip" rather than marking every
   * local user as missing.
   *
   * @return list of Keycloak users with their resolved realm role names, or empty on any failure
   */
  public List<KeycloakUserDto> fetchUsers() {
    if (!properties.isEnabled() || properties.getAdminUrl() == null) {
      log.debug("Keycloak sync disabled or admin URL missing");
      return Collections.emptyList();
    }

    try {
      String token = getAccessToken();

      List<KeycloakUserDto> users =
          RestClient.builder()
              .baseUrl(properties.getAdminUrl())
              .build()
              .get()
              .uri("/admin/realms/{realm}/users", properties.getRealm())
              .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
              .retrieve()
              .body(new ParameterizedTypeReference<List<KeycloakUserDto>>() {});

      if (users == null) {
        return Collections.emptyList();
      }

      return users.stream()
          .map(
              u -> {
                Set<String> roles = fetchUserRoles(u.id(), token);
                return new KeycloakUserDto(u.id(), u.username(), u.email(), u.enabled(), roles);
              })
          .toList();

    } catch (Exception e) {
      log.error("Failed to fetch users from Keycloak", e);
      return Collections.emptyList();
    }
  }

  private Set<String> fetchUserRoles(UUID userId, String token) {
    try {
      List<Map<String, Object>> roles =
          RestClient.builder()
              .baseUrl(properties.getAdminUrl())
              .build()
              .get()
              .uri(
                  "/admin/realms/{realm}/users/{id}/role-mappings/realm",
                  properties.getRealm(),
                  userId)
              .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
              .retrieve()
              .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

      if (roles == null) {
        return Collections.emptySet();
      }
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
      Map response =
          RestClient.builder()
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
