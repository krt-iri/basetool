package de.greluc.krt.iri.basetool.frontend.exception;

import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler} verifying that backend Problem+JSON
 * error {@code code} values are correctly mapped onto localized message keys for
 * both HTML (error page) and JSON (AJAX/toast) rendering paths. Covers the main
 * error classes from tasks 7-12: optimistic lock -> reload hint, access denied,
 * validation with field errors, service-unavailable / backend timeout.
 */
class GlobalExceptionHandlerTest {

    private MessageSource messageSource;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        messageSource = Mockito.mock(MessageSource.class);
        // Default: resolver returns the key itself — makes assertions simple.
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        handler = new GlobalExceptionHandler(messageSource);
    }

    private HttpServletRequest jsonRequest() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(req.getRequestURI()).thenReturn("/unit-test");
        return req;
    }

    private HttpServletRequest htmlRequest() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Accept")).thenReturn("text/html");
        when(req.getRequestURI()).thenReturn("/unit-test");
        return req;
    }

    @Test
    void optimisticLock_ShouldRenderJsonWithReloadHintAndLocalizedKey() {
        BackendServiceException ex = new BackendServiceException(
                "conflict", null, 409, "OPTIMISTIC_LOCK", "corr-1", List.of(), "detail");

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("OPTIMISTIC_LOCK", body.get("code"));
        assertEquals(409, body.get("status"));
        assertEquals("error.optimisticLock", body.get("message"));
        assertEquals("error.409.title", body.get("title"));
        assertEquals("corr-1", body.get("correlationId"));
        assertEquals(Boolean.TRUE, body.get("reloadHint"));
    }

    @Test
    void accessDenied_ShouldMapToForbiddenKey_AndRenderErrorPageForHtml() {
        BackendServiceException ex = new BackendServiceException(
                "forbidden", null, 403, "ACCESS_DENIED", "corr-403", List.of(), null);
        Model model = new ConcurrentModel();

        Object result = handler.handleBackendServiceException(ex, htmlRequest(), model);

        assertEquals("error/error", result);
        assertEquals("error.403.title", model.getAttribute("error"));
        assertEquals("error.forbidden", model.getAttribute("message"));
        assertEquals("403", model.getAttribute("status"));
        assertEquals("ACCESS_DENIED", model.getAttribute("errorCode"));
        assertEquals("corr-403", model.getAttribute("correlationId"));
    }

    @Test
    void validationFailed_ShouldPropagateFieldErrorsInJsonBody() {
        List<BackendServiceException.FieldError> fieldErrors = List.of(
                new BackendServiceException.FieldError("name", "must not be blank"),
                new BackendServiceException.FieldError("amount", "must be positive"));
        BackendServiceException ex = new BackendServiceException(
                "validation", null, 400, "VALIDATION_FAILED", "corr-400", fieldErrors, null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_FAILED", body.get("code"));
        assertEquals("error.validation.generic", body.get("message"));
        assertTrue(body.containsKey("fieldErrors"));
        @SuppressWarnings("unchecked")
        List<BackendServiceException.FieldError> out =
                (List<BackendServiceException.FieldError>) body.get("fieldErrors");
        assertEquals(2, out.size());
        assertEquals("name", out.get(0).field());
    }

    @Test
    void serviceUnavailable_ShouldMapToUnavailableKey() {
        BackendServiceException ex = new BackendServiceException(
                "cb open", null, 503, BackendServiceException.CODE_SERVICE_UNAVAILABLE, null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("error.unavailable", body.get("message"));
    }

    @Test
    void backendTimeout_ShouldMapToBackendTimeoutKey() {
        BackendServiceException ex = new BackendServiceException(
                "timeout", null, 504, BackendServiceException.CODE_BACKEND_TIMEOUT, null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("error.backendTimeout", body.get("message"));
    }

    @Test
    void unknownCode_ShouldFallBackToUnexpectedKey() {
        BackendServiceException ex = new BackendServiceException(
                "boom", null, 500, BackendServiceException.CODE_UNKNOWN, "corr-500", List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("error.unexpected", body.get("message"));
        assertEquals("corr-500", body.get("correlationId"));
    }
}
