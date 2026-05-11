package de.greluc.krt.iri.basetool.backend.exception;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the most important RFC7807 error responses produced by {@link GlobalExceptionHandler}:
 * every handler must return {@code application/problem+json} and carry the stable
 * {@code code} / {@code correlationId} extension fields so that the frontend can map the
 * problem to a localized message.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        AppProblemProperties props = new AppProblemProperties();
        props.setBaseUri("https://iri-base.org/problems/");
        // Use the real messages bundle so the test catches missing or wrong i18n keys.
        // Locale is forced to English to keep these assertions stable regardless of the
        // JVM default locale on the developer / CI machine.
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        handler = new GlobalExceptionHandler(props, messageSource);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static void assertCommon(ResponseEntity<ProblemDetail> response, HttpStatus expectedStatus,
                                     String expectedCode) {
        assertEquals(expectedStatus.value(), response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals(expectedStatus.value(), pd.getStatus());
        assertNotNull(pd.getType());
        assertNotNull(pd.getInstance());
        assertNotNull(pd.getProperties());
        assertEquals(expectedCode, pd.getProperties().get("code"));
        assertNotNull(pd.getProperties().get("correlationId"));
        assertTrue(((String) pd.getProperties().get("correlationId")).length() > 0);
    }

    @Test
    void handleOptimisticLocking_springVariant_returns409WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("Mission", "id"), request);

        assertCommon(resp, HttpStatus.CONFLICT, GlobalExceptionHandler.CODE_OPTIMISTIC_LOCK);
        assertEquals("Concurrency conflict", resp.getBody().getTitle());
    }

    @Test
    void handleOptimisticLocking_jpaVariant_returns409WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleOptimisticLockingFailure(
                new OptimisticLockException("stale"), request);

        assertCommon(resp, HttpStatus.CONFLICT, GlobalExceptionHandler.CODE_OPTIMISTIC_LOCK);
    }

    @Test
    void handlePessimisticLocking_returns409WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handlePessimisticLocking(
                new PessimisticLockingFailureException("busy"), request);

        assertCommon(resp, HttpStatus.CONFLICT, GlobalExceptionHandler.CODE_PESSIMISTIC_LOCK);
        assertEquals("Resource busy", resp.getBody().getTitle());
    }

    @Test
    void handleAuthentication_returns401WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleAuthentication(
                new BadCredentialsException("bad creds"), request);

        assertCommon(resp, HttpStatus.UNAUTHORIZED, GlobalExceptionHandler.CODE_UNAUTHENTICATED);
    }

    @Test
    void handleAccessDenied_legacyException_returns403WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleAccessDenied(
                new AccessDeniedException("Access is denied"), request);

        assertCommon(resp, HttpStatus.FORBIDDEN, GlobalExceptionHandler.CODE_ACCESS_DENIED);
    }

    @Test
    void handleAccessDenied_authorizationDeniedException_returns403WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleAccessDenied(
                new AuthorizationDeniedException("Access denied",
                        new AuthorizationResult() {
                            @Override
                            public boolean isGranted() {
                                return false;
                            }
                        }),
                request);

        assertCommon(resp, HttpStatus.FORBIDDEN, GlobalExceptionHandler.CODE_ACCESS_DENIED);
    }

    @Test
    void handleAccessDenied_fallsBackToGenericDetailIfBlank() {
        ResponseEntity<ProblemDetail> resp = handler.handleAccessDenied(
                new AccessDeniedException(""), request);

        assertEquals("You are not authorized to perform this action.",
                resp.getBody().getDetail());
    }

    @Test
    void handleValidationExceptions_returns400WithFieldErrors() throws NoSuchMethodException {
        // Build a BindingResult with a single field error.
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ProblemDetail> resp = handler.handleValidationExceptions(ex, request);

        assertCommon(resp, HttpStatus.BAD_REQUEST, GlobalExceptionHandler.CODE_VALIDATION_FAILED);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) resp.getBody().getProperties().get("errors");
        assertEquals("must not be blank", errors.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> fieldErrors = (List<Map<String, String>>)
                resp.getBody().getProperties().get("fieldErrors");
        assertEquals(1, fieldErrors.size());
        assertEquals("name", fieldErrors.get(0).get("field"));
    }

    @SuppressWarnings("unused")
    private void dummy(String s) { /* test target */ }

    @Test
    void handleValidationExceptions_logsWarn_withMethodUriAndFieldErrors_butWithoutRejectedValue()
            throws NoSuchMethodException {
        // Given: a binding result containing a field error whose rejected value would normally
        // contain user input / PII (e.g. recipientHandle). The improved logging MUST log the
        // field name and constraint message but MUST NOT log the rejected value itself.
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "handover");
        bindingResult.addError(new FieldError(
                "handover", "recipientHandle", "secret-pii-handle", false,
                new String[]{"NotBlank"}, null, "must not be blank"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/orders/abc/handovers");

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // When
            handler.handleValidationExceptions(ex, request);

            // Then
            ILoggingEvent warn = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains("Validation failed"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected WARN log for validation failure"));

            String formatted = warn.getFormattedMessage();
            assertTrue(formatted.contains("POST"), "log must include HTTP method");
            assertTrue(formatted.contains("/api/v1/orders/abc/handovers"),
                    "log must include the request URI");
            assertTrue(formatted.contains("recipientHandle"),
                    "log must include the offending field name");
            assertTrue(formatted.contains("must not be blank"),
                    "log must include the constraint message");
            assertTrue(formatted.contains("correlationId="),
                    "log must include the correlationId for cross-referencing");
            assertFalse(formatted.contains("secret-pii-handle"),
                    "log must NOT leak the rejected user-provided value (PII)");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void handleConstraintViolation_returns400WithFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("page");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be >= 0");
        ConstraintViolationException ex = new ConstraintViolationException("bad", Set.of(violation));

        ResponseEntity<ProblemDetail> resp = handler.handleConstraintViolation(ex, request);

        assertCommon(resp, HttpStatus.BAD_REQUEST, GlobalExceptionHandler.CODE_CONSTRAINT_VIOLATION);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> fieldErrors = (List<Map<String, String>>)
                resp.getBody().getProperties().get("fieldErrors");
        assertEquals(1, fieldErrors.size());
        assertEquals("page", fieldErrors.get(0).get("field"));
        assertEquals("must be >= 0", fieldErrors.get(0).get("message"));
    }

    @Test
    void handleTypeMismatch_returns400WithCode() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", null, new IllegalArgumentException("nope"));

        ResponseEntity<ProblemDetail> resp = handler.handleTypeMismatch(ex, request);

        assertCommon(resp, HttpStatus.BAD_REQUEST, GlobalExceptionHandler.CODE_TYPE_MISMATCH);
        assertTrue(resp.getBody().getDetail().contains("page"));
    }

    @Test
    void handleMethodNotSupported_returns405WithCode() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT");

        ResponseEntity<ProblemDetail> resp = handler.handleMethodNotSupported(ex, request);

        assertCommon(resp, HttpStatus.METHOD_NOT_ALLOWED, GlobalExceptionHandler.CODE_METHOD_NOT_ALLOWED);
        assertTrue(resp.getBody().getDetail().contains("PUT"));
    }

    @Test
    void handleNotFound_entityNotFoundException_returns404WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleNotFound(
                new EntityNotFoundException("Mission 42"), request);

        assertCommon(resp, HttpStatus.NOT_FOUND, GlobalExceptionHandler.CODE_NOT_FOUND);
    }

    @Test
    void handleNotFound_noSuchElement_returns404WithCode() {
        ResponseEntity<ProblemDetail> resp = handler.handleNotFound(
                new NoSuchElementException("gone"), request);

        assertCommon(resp, HttpStatus.NOT_FOUND, GlobalExceptionHandler.CODE_NOT_FOUND);
    }

    @Test
    void handleAllExceptions_returns500WithCorrelationIdAndGenericDetail() {
        ResponseEntity<ProblemDetail> resp = handler.handleAllExceptions(
                new RuntimeException("secret internal detail"), request);

        assertCommon(resp, HttpStatus.INTERNAL_SERVER_ERROR, GlobalExceptionHandler.CODE_INTERNAL_ERROR);
        // Must NOT leak the internal exception message to the client.
        String detail = resp.getBody().getDetail();
        assertNotNull(detail);
        assertTrue(!detail.contains("secret internal detail"),
                "internal exception message must not leak to client");
    }
}
