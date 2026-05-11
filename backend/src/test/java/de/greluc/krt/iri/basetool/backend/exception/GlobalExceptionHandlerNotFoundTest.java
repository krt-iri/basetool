package de.greluc.krt.iri.basetool.backend.exception;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that service-level "not found" exceptions are translated into a proper
 * RFC7807 404 response instead of bubbling up into the generic 500 handler.
 */
class GlobalExceptionHandlerNotFoundTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        AppProblemProperties props = new AppProblemProperties();
        props.setBaseUri("https://iri-base.org/problems/");
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        handler = new GlobalExceptionHandler(props, messageSource);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/missions/abc");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void handleNotFound_shouldReturn404ProblemJson() {
        // Given
        NotFoundException ex = new NotFoundException("Mission not found");

        // When
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex, request);

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals(404, pd.getStatus());
        assertEquals("Not Found", pd.getTitle());
        assertEquals("Mission not found", pd.getDetail());
        assertEquals(URI.create("https://iri-base.org/problems/not-found"), pd.getType());
        assertEquals(URI.create("/api/v1/missions/abc"), pd.getInstance());
        assertNotNull(pd.getProperties());
        assertEquals(GlobalExceptionHandler.CODE_NOT_FOUND, pd.getProperties().get("code"));
        assertNotNull(pd.getProperties().get("correlationId"));
    }

    @Test
    void handleNotFound_shouldFallBackToGenericDetailIfNull() {
        NotFoundException ex = new NotFoundException(null);

        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("The requested resource was not found.", pd.getDetail());
    }
}
