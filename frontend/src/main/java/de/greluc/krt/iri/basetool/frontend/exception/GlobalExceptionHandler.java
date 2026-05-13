package de.greluc.krt.iri.basetool.frontend.exception;

import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central Spring MVC error mapping for the frontend module.
 *
 * <p>Translates {@link BackendServiceException} (raised by the WebClient layer when the backend
 * returns an RFC7807 Problem+JSON response or when a Resilience4j fallback fires) into localized,
 * user-facing messages using {@link MessageSource}. The stable Problem {@code code} (e.g. {@code
 * OPTIMISTIC_LOCK}, {@code ACCESS_DENIED}, {@code VALIDATION_FAILED}) is mapped onto a well-defined
 * set of {@code error.*} message keys — no hardcoded user-facing strings are emitted (AGENTS.md:
 * DYNAMIC TRANSLATION ONLY).
 *
 * <p>AJAX/JSON requests receive a compact JSON body so that the client-side {@code
 * window.showError(problem)} function can render a KRT-styled toast, while regular navigation
 * requests render the error page with a localized title and explanation.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private static final Map<String, String> CODE_TO_MESSAGE_KEY = buildCodeMapping();
  private static final String DEFAULT_MESSAGE_KEY = "error.unexpected";
  private static final String DEFAULT_TITLE_KEY = "error.generic.title";

  private final MessageSource messageSource;

  /**
   * Renders an i18n-resolved error page (or JSON toast snippet for XHR clients) from an RFC-7807
   * problem returned by the backend, preserving the correlation id for cross-tier debugging.
   */
  @ExceptionHandler(BackendServiceException.class)
  public Object handleBackendServiceException(
      @NotNull BackendServiceException ex,
      @NotNull HttpServletRequest request,
      @NotNull Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    HttpStatus status = resolveStatus(ex.getStatusCode());
    String messageKey = CODE_TO_MESSAGE_KEY.getOrDefault(ex.getProblemCode(), DEFAULT_MESSAGE_KEY);
    String localizedMessage = resolve(messageKey, locale, ex.getReadableErrorMessage());
    String localizedTitle =
        resolve(titleKeyForStatus(status), locale, resolve(DEFAULT_TITLE_KEY, locale, "Error"));

    log.warn(
        "Backend error propagated to user: code={}, status={}, correlationId={}, uri={}",
        ex.getProblemCode(),
        status.value(),
        ex.getCorrelationId(),
        request.getRequestURI());

    if (wantsJson(request)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("code", ex.getProblemCode());
      body.put("status", status.value());
      body.put("title", localizedTitle);
      body.put("message", localizedMessage);
      if (ex.getCorrelationId() != null) {
        body.put("correlationId", ex.getCorrelationId());
      }
      if (!ex.getFieldErrors().isEmpty()) {
        body.put("fieldErrors", ex.getFieldErrors());
      }
      body.put(
          "reloadHint",
          ex.getProblemCode().equals("OPTIMISTIC_LOCK") || ex.getProblemCode().equals("CONFLICT"));
      return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    model.addAttribute("error", localizedTitle);
    model.addAttribute("message", localizedMessage);
    model.addAttribute("status", String.valueOf(status.value()));
    model.addAttribute("errorCode", ex.getProblemCode());
    if (ex.getCorrelationId() != null) {
      model.addAttribute("correlationId", ex.getCorrelationId());
    }
    return "error/error";
  }

  /** Renders the 404 error page for unmapped static resource / page requests. */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleNoResourceFoundException(
      @NotNull NoResourceFoundException e, @NotNull Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    model.addAttribute("error", resolve("error.404.title", locale, "Not Found"));
    model.addAttribute(
        "message",
        resolve("error.404.message", locale, "The requested resource could not be found."));
    model.addAttribute("status", "404");
    return "error/error";
  }

  /**
   * Renders a 400 error page when an MVC path / query parameter cannot be coerced to its declared
   * type. The rejected value is intentionally not logged because it may carry PII.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object handleTypeMismatch(
      @NotNull MethodArgumentTypeMismatchException ex,
      @NotNull Model model,
      @NotNull HttpServletRequest request) {
    // Do NOT log ex.getValue() - request parameter values may carry PII.
    log.warn(
        "Frontend type mismatch for {} {} [parameter={}, targetType={}]",
        request.getMethod(),
        request.getRequestURI(),
        ex.getName(),
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "n/a");
    Locale locale = LocaleContextHolder.getLocale();
    String message =
        resolve("error.validation.failed", locale, "Invalid parameter " + ex.getName());
    if (wantsJson(request)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("code", "VALIDATION_FAILED");
      body.put("status", 400);
      body.put("title", resolve("error.400.title", locale, "Bad Request"));
      body.put("message", message);
      return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }
    model.addAttribute("error", resolve("error.400.title", locale, "Bad Request"));
    model.addAttribute("message", message);
    model.addAttribute("status", "400");
    return "error/error";
  }

  /**
   * Translates Spring Security authorization failures (raised by {@code @PreAuthorize} checks
   * within controllers) into a 403 response page or a JSON body for AJAX callers, instead of
   * letting them fall through to the generic 500 handler. Without this mapping, a missing role
   * would surface to the user as an opaque "Internal Server Error" — which both hides the real
   * cause and contradicts the standard semantics for HTTP 403.
   */
  @ExceptionHandler({
    org.springframework.security.access.AccessDeniedException.class,
    org.springframework.security.authorization.AuthorizationDeniedException.class
  })
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Object handleAccessDenied(
      @NotNull Exception ex, @NotNull HttpServletRequest request, @NotNull Model model) {
    Locale locale = LocaleContextHolder.getLocale();
    String title = resolve("error.403.title", locale, "Forbidden");
    String message = resolve("error.forbidden", locale, "Access denied.");
    log.warn(
        "Access denied for {} {} [exception={}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.getClass().getSimpleName(),
        ex.getMessage());
    if (wantsJson(request)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("code", "ACCESS_DENIED");
      body.put("status", 403);
      body.put("title", title);
      body.put("message", message);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body);
    }
    model.addAttribute("error", title);
    model.addAttribute("message", message);
    model.addAttribute("status", "403");
    return "error/error";
  }

  /**
   * Catch-all fallback. Renders a 500 error page; unwraps a {@link BackendServiceException} cause
   * to propagate the backend's status code (e.g. 503 / 504) rather than masking it as 500.
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleException(
      @NotNull Exception e, @NotNull Model model, @NotNull HttpServletRequest request) {
    Locale locale = LocaleContextHolder.getLocale();
    String status = "500";
    String titleKey = DEFAULT_TITLE_KEY;
    String messageKey = DEFAULT_MESSAGE_KEY;

    // Unwrap well-known frameworks to give users a meaningful hint.
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof WebClientResponseException wcre) {
        status = String.valueOf(wcre.getStatusCode().value());
        titleKey = titleKeyForStatus(resolveStatus(wcre.getStatusCode().value()));
        messageKey = CODE_TO_MESSAGE_KEY.getOrDefault("UNKNOWN", DEFAULT_MESSAGE_KEY);
        break;
      }
      cause = cause.getCause();
    }

    log.error(
        "Unexpected frontend error for {} {} [exception={}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getClass().getSimpleName(),
        e.getMessage(),
        e);
    model.addAttribute("error", resolve(titleKey, locale, "Unexpected Error"));
    model.addAttribute("message", resolve(messageKey, locale, "An unexpected error occurred."));
    model.addAttribute("status", status);
    return "error/error";
  }

  // ------------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------------

  private static @NotNull HttpStatus resolveStatus(int statusCode) {
    HttpStatus resolved = HttpStatus.resolve(statusCode);
    return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static @NotNull String titleKeyForStatus(@NotNull HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "error.400.title";
      case UNAUTHORIZED -> "error.401.title";
      case FORBIDDEN -> "error.403.title";
      case NOT_FOUND -> "error.404.title";
      case CONFLICT -> "error.409.title";
      case LOCKED -> "error.423.title";
      case SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "error.503.title";
      default -> DEFAULT_TITLE_KEY;
    };
  }

  private @NotNull String resolve(
      @NotNull String key, @NotNull Locale locale, @NotNull String fallback) {
    try {
      return messageSource.getMessage(key, null, fallback, locale) != null
          ? messageSource.getMessage(key, null, fallback, locale)
          : fallback;
    } catch (Exception ex) {
      return fallback;
    }
  }

  private static boolean wantsJson(@NotNull HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
      return true;
    }
    String requestedWith = request.getHeader("X-Requested-With");
    return "XMLHttpRequest".equalsIgnoreCase(requestedWith);
  }

  private static @NotNull Map<String, String> buildCodeMapping() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("OPTIMISTIC_LOCK", "error.optimisticLock");
    m.put("PESSIMISTIC_LOCK", "error.pessimisticLock");
    m.put("ACCESS_DENIED", "error.forbidden");
    m.put("FORBIDDEN_ROLE", "error.forbidden");
    m.put("UNAUTHENTICATED", "error.unauthenticated");
    m.put("VALIDATION_FAILED", "error.validation.generic");
    m.put("CONSTRAINT_VIOLATION", "error.validation.generic");
    m.put("TYPE_MISMATCH", "error.validation.generic");
    m.put("MALFORMED_REQUEST", "error.validation.generic");
    m.put("NOT_FOUND", "error.notFound");
    m.put("METHOD_NOT_SUPPORTED", "error.methodNotSupported");
    m.put("CONFLICT", "error.conflict.duplicate");
    m.put("DATA_INTEGRITY", "error.conflict.duplicate");
    m.put("LOCKED", "error.pessimisticLock");
    m.put(BackendServiceException.CODE_SERVICE_UNAVAILABLE, "error.unavailable");
    m.put(BackendServiceException.CODE_BACKEND_TIMEOUT, "error.backendTimeout");
    m.put(BackendServiceException.CODE_UNKNOWN, "error.unexpected");
    return Map.copyOf(m);
  }
}
