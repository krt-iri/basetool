package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class JobOrderHandoverReportProxyControllerTest {

  @Mock private WebClient webClient;

  @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;

  @Mock private WebClient.ResponseSpec responseSpec;

  @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;

  @Mock private WebClient.RequestBodySpec requestBodySpec;

  @InjectMocks private JobOrderHandoverReportProxyController controller;

  // -------------------------------------------------------------------------
  // GET /{jobOrderId}/handovers/{handoverId}/report
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void downloadHandoverReport_shouldReturnPdfBytes_whenBackendRespondsOk() {
    // Given
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    byte[] fakePdf = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF magic bytes

    doReturn(requestHeadersUriSpec).when(webClient).get();
    doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
    doReturn(requestHeadersSpec).when(requestHeadersSpec).headers(any(Consumer.class));
    doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    doReturn(Mono.just(fakePdf)).when(responseSpec).bodyToMono(byte[].class);

    // When
    ResponseEntity<byte[]> response =
        controller.downloadHandoverReport(jobOrderId, handoverId, null);

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertArrayEquals(fakePdf, response.getBody());
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    String disposition = response.getHeaders().getFirst("Content-Disposition");
    assertNotNull(disposition);
    assertTrue(disposition.contains("attachment"));
    assertTrue(disposition.contains(".pdf"));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void downloadHandoverReport_shouldThrowResponseStatusException_whenBackendReturns404() {
    // Given
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();

    doReturn(requestHeadersUriSpec).when(webClient).get();
    doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
    doReturn(requestHeadersSpec).when(requestHeadersSpec).headers(any(Consumer.class));
    doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    doReturn(Mono.error(WebClientResponseException.create(404, "Not Found", null, null, null)))
        .when(responseSpec)
        .bodyToMono(byte[].class);

    // When & Then
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.downloadHandoverReport(jobOrderId, handoverId, null));
    assertEquals(404, ex.getStatusCode().value());
  }

  // -------------------------------------------------------------------------
  // POST /{jobOrderId}/handovers/report/preview
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void previewHandoverReport_shouldReturnPdfBytes_whenBackendRespondsOk() {
    // Given
    UUID jobOrderId = UUID.randomUUID();
    byte[] fakePdf = new byte[] {0x25, 0x50, 0x44, 0x46};
    Map<String, Object> payload = Map.of("jobOrderNumber", "#42", "recipientHandle", "Pilot");

    doReturn(requestBodyUriSpec).when(webClient).post();
    doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
    doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
    doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
    doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    doReturn(Mono.just(fakePdf)).when(responseSpec).bodyToMono(byte[].class);

    // When
    ResponseEntity<byte[]> response = controller.previewHandoverReport(jobOrderId, payload);

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertArrayEquals(fakePdf, response.getBody());
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    String disposition = response.getHeaders().getFirst("Content-Disposition");
    assertNotNull(disposition);
    assertTrue(disposition.contains("attachment"));
    assertTrue(disposition.contains(".pdf"));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void previewHandoverReport_shouldThrowResponseStatusException_whenBackendReturns400() {
    // Given
    UUID jobOrderId = UUID.randomUUID();
    Map<String, Object> payload = Map.of();

    doReturn(requestBodyUriSpec).when(webClient).post();
    doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
    doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
    doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
    doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    doReturn(Mono.error(WebClientResponseException.create(400, "Bad Request", null, null, null)))
        .when(responseSpec)
        .bodyToMono(byte[].class);

    // When & Then
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.previewHandoverReport(jobOrderId, payload));
    assertEquals(400, ex.getStatusCode().value());
  }
}
