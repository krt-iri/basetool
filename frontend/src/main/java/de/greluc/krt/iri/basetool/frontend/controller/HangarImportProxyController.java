package de.greluc.krt.iri.basetool.frontend.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontend proxy for the Fleetview JSON import endpoint.
 *
 * <p>Receives the multipart file from the browser, forwards it to the backend {@code POST
 * /api/v1/hangar/import/fleetview} via the authenticated {@link WebClient} (which automatically
 * attaches the OAuth2 token), and returns the backend response as-is to the browser.
 */
@RestController
@RequestMapping("/hangar/import")
@RequiredArgsConstructor
@Slf4j
public class HangarImportProxyController {

  private final WebClient webClient;

  /**
   * Proxies a Fleetview JSON file upload from the browser to the backend import endpoint.
   *
   * @param file the uploaded {@code fleetview.json} file
   * @return the backend {@code FleetviewImportResponseDto} as a raw JSON map
   */
  @PostMapping(value = "/fleetview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<?, ?>> importFleetview(
      @RequestParam("file") @NotNull MultipartFile file) {
    try {
      byte[] bytes = file.getBytes();
      String originalFilename =
          file.getOriginalFilename() != null ? file.getOriginalFilename() : "fleetview.json";

      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      builder
          .part(
              "file",
              new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                  return originalFilename;
                }
              })
          .contentType(MediaType.APPLICATION_OCTET_STREAM);

      Map<?, ?> result =
          webClient
              .post()
              .uri("/api/v1/hangar/import/fleetview")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(BodyInserters.fromMultipartData(builder.build()))
              .retrieve()
              .bodyToMono(Map.class)
              .block();

      return ResponseEntity.ok(result);
    } catch (WebClientResponseException e) {
      log.warn(
          "Fleetview import proxy: backend returned {} — {}", e.getStatusCode(), e.getMessage());
      throw new ResponseStatusException(e.getStatusCode(), e.getMessage());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("Fleetview import proxy: unexpected error", e);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "An unexpected error occurred during import.");
    }
  }
}
