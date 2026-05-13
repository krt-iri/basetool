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

    // ─── Ajax-detection via X-Requested-With ────────────────────────────────

    @Test
    void wantsJson_viaXRequestedWith_isHonoured() {
        // Given — a non-Accept-JSON request that's clearly Ajax
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("Accept")).thenReturn("text/html");
        when(req.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
        when(req.getRequestURI()).thenReturn("/ajax");

        BackendServiceException ex = new BackendServiceException(
                "x", null, 400, "VALIDATION_FAILED", null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, req, new ConcurrentModel());

        // The X-Requested-With header alone must promote the response to JSON
        // so legacy XHR callers don't accidentally get a full HTML error page.
        assertInstanceOf(ResponseEntity.class, result);
    }

    // ─── handleBackendServiceException — model branch (HTML) details ────────

    @Test
    void backendException_html_withoutCorrelationId_doesNotAddCorrelationIdAttribute() {
        // Given — no correlation id; the controller must NOT push a `null`
        // attribute that would render as the literal "null" string.
        BackendServiceException ex = new BackendServiceException(
                "x", null, 404, "NOT_FOUND", /*correlationId*/ null, List.of(), null);
        Model model = new ConcurrentModel();

        handler.handleBackendServiceException(ex, htmlRequest(), model);

        assertEquals(null, model.getAttribute("correlationId"));
    }

    @Test
    void backendException_json_withoutCorrelationId_doesNotIncludeKey() {
        // Same contract for the JSON branch: an absent correlation id must
        // NOT surface as `"correlationId":null` — the JSON body is consumed
        // by the toast renderer which checks `key in body`.
        BackendServiceException ex = new BackendServiceException(
                "x", null, 404, "NOT_FOUND", null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertTrue(!body.containsKey("correlationId"), "correlationId key must be absent, not null");
    }

    @Test
    void backendException_unmappedStatusCode_fallsBackTo500() {
        // Given an unusual status code that HttpStatus.resolve() doesn't know.
        // The handler must fall back to 500 — not propagate the unknown code.
        BackendServiceException ex = new BackendServiceException(
                "weird", null, 599, "UNKNOWN", null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void conflictCode_setsReloadHintTrue_inJsonBody() {
        // The reloadHint flag is set for both OPTIMISTIC_LOCK and CONFLICT
        // codes — frontend uses it to auto-reload the page after dismissing
        // the toast. A regression here would leave the user on stale data.
        BackendServiceException ex = new BackendServiceException(
                "x", null, 409, "CONFLICT", null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(Boolean.TRUE, body.get("reloadHint"));
    }

    @Test
    void nonConflictNonOptimisticLock_setsReloadHintFalse() {
        BackendServiceException ex = new BackendServiceException(
                "x", null, 400, "VALIDATION_FAILED", null, List.of(), null);

        Object result = handler.handleBackendServiceException(ex, jsonRequest(), new ConcurrentModel());

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(Boolean.FALSE, body.get("reloadHint"));
    }

    // ─── handleNoResourceFoundException ─────────────────────────────────────

    @Test
    void noResourceFound_setsModelAttributes_andReturnsErrorView() throws Exception {
        // The 3-arg constructor is the Spring 6.2+ signature
        // (method, resourcePath, message).
        org.springframework.web.servlet.resource.NoResourceFoundException ex =
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/missing", "not there");
        Model model = new ConcurrentModel();

        String view = handler.handleNoResourceFoundException(ex, model);

        assertEquals("error/error", view);
        assertEquals("error.404.title", model.getAttribute("error"));
        assertEquals("error.404.message", model.getAttribute("message"));
        assertEquals("404", model.getAttribute("status"));
    }

    // ─── handleTypeMismatch ─────────────────────────────────────────────────

    @Test
    void typeMismatch_jsonRequest_returns400JsonBody() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                Mockito.mock(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        Mockito.<Class<?>>when(ex.getRequiredType()).thenReturn((Class) java.util.UUID.class);

        Object result = handler.handleTypeMismatch(ex, new ConcurrentModel(), jsonRequest());

        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_FAILED", body.get("code"));
        assertEquals(400, body.get("status"));
        assertEquals("error.400.title", body.get("title"));
    }

    @Test
    void typeMismatch_htmlRequest_returnsErrorView() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                Mockito.mock(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        Model model = new ConcurrentModel();

        Object result = handler.handleTypeMismatch(ex, model, htmlRequest());

        assertEquals("error/error", result);
        assertEquals("error.400.title", model.getAttribute("error"));
        assertEquals("400", model.getAttribute("status"));
    }

    @Test
    void typeMismatch_withNullRequiredType_doesNotNPE() {
        // Defensive: getRequiredType() returning null is rare but valid.
        // Without the null-check the handler would NPE when logging.
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                Mockito.mock(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("x");
        Mockito.<Class<?>>when(ex.getRequiredType()).thenReturn(null);

        assertEquals("error/error", handler.handleTypeMismatch(ex, new ConcurrentModel(), htmlRequest()));
    }

    // ─── handleAccessDenied ─────────────────────────────────────────────────

    @Test
    void accessDenied_springSecurity_jsonRequest_returns403Json() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("forbidden");

        Object result = handler.handleAccessDenied(ex, jsonRequest(), new ConcurrentModel());

        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("ACCESS_DENIED", body.get("code"));
        assertEquals(403, body.get("status"));
        assertEquals("error.403.title", body.get("title"));
    }

    @Test
    void accessDenied_authorizationDenied_htmlRequest_returnsErrorView() {
        // AuthorizationDeniedException is the Spring Security 6+ replacement —
        // the handler must catch both because @PreAuthorize raises the new one
        // while older code paths still raise the legacy AccessDeniedException.
        org.springframework.security.authorization.AuthorizationDeniedException ex =
                new org.springframework.security.authorization.AuthorizationDeniedException("denied");
        Model model = new ConcurrentModel();

        Object result = handler.handleAccessDenied(ex, htmlRequest(), model);

        assertEquals("error/error", result);
        assertEquals("error.403.title", model.getAttribute("error"));
        assertEquals("error.forbidden", model.getAttribute("message"));
        assertEquals("403", model.getAttribute("status"));
    }

    // ─── handleException (generic catch-all) ────────────────────────────────

    @Test
    void genericException_returnsErrorView_with500Default() {
        RuntimeException unexpected = new RuntimeException("boom");
        Model model = new ConcurrentModel();

        String view = handler.handleException(unexpected, model, htmlRequest());

        assertEquals("error/error", view);
        assertEquals("error.generic.title", model.getAttribute("error"));
        assertEquals("error.unexpected", model.getAttribute("message"));
        assertEquals("500", model.getAttribute("status"));
    }

    @Test
    void genericException_unwrapsWebClientResponseException_andUsesItsStatus() {
        // Given a WebClient call that escaped the BackendApiClient wrapping
        // (rare but possible — e.g. an unrelated WebClient instance) and
        // surfaced via a wrapper RuntimeException. The handler must walk the
        // cause chain and adopt the upstream HTTP status for the error view.
        org.springframework.web.reactive.function.client.WebClientResponseException inner =
                org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        503, "Service Unavailable", null, null, null);
        RuntimeException wrapped = new RuntimeException("outer", inner);

        Model model = new ConcurrentModel();
        String view = handler.handleException(wrapped, model, htmlRequest());

        assertEquals("error/error", view);
        assertEquals("503", model.getAttribute("status"),
                "When the cause is a WebClientResponseException, surface its status");
        assertEquals("error.503.title", model.getAttribute("error"));
    }
}
