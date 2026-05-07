package de.greluc.krt.iri.basetool.frontend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontend proxy for the "delete all ships" endpoint.
 *
 * <p>Forwards {@code DELETE /hangar/ships/all} to the backend
 * {@code DELETE /api/v1/hangar/ships} via the authenticated {@link WebClient}
 * (which automatically attaches the OAuth2 token), and returns 204 No Content on success.
 */
@RestController
@RequestMapping("/hangar/ships")
@RequiredArgsConstructor
@Slf4j
public class HangarDeleteAllProxyController {

    private final WebClient webClient;

    /**
     * Proxies a "delete all ships" request to the backend.
     *
     * @return 204 No Content on success
     */
    @DeleteMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAllShips() {
        try {
            webClient.delete()
                    .uri("/api/v1/hangar/ships")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException e) {
            log.warn("Delete-all-ships proxy: backend returned {} — {}", e.getStatusCode(), e.getMessage());
            throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Delete-all-ships proxy: unexpected error", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred while deleting all ships.");
        }
    }
}
