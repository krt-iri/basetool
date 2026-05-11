package de.greluc.krt.iri.basetool.backend.exception;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central RFC7807 exception handler.
 *
 * <p>Every error response carries the standard Problem Details fields (type, title, status,
 * detail, instance) plus two additional, stable extension fields:
 * <ul>
 *     <li>{@code code} - a stable, machine readable error code used by the frontend to select
 *         the appropriate localized message (e.g. {@code OPTIMISTIC_LOCK}). The code must never
 *         change once published, even if the user-facing {@code detail} text is reworded.</li>
 *     <li>{@code correlationId} - a per-request UUID used to correlate the user-visible error
 *         with a server log entry without leaking stack traces or internal implementation
 *         details to the client.</li>
 * </ul>
 *
 * <p>Expected, user-driven errors (4xx) are logged at {@code WARN}/{@code DEBUG} without a
 * stack trace; unexpected internal errors (5xx) are logged at {@code ERROR} with the full
 * stack trace and the correlation id to aid post-mortem debugging.
 */
@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** Stable error codes exposed via the {@code code} extension property. */
    public static final String CODE_OPTIMISTIC_LOCK = "OPTIMISTIC_LOCK";
    public static final String CODE_PESSIMISTIC_LOCK = "PESSIMISTIC_LOCK";
    public static final String CODE_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String CODE_UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CODE_CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";
    public static final String CODE_DUPLICATE_ENTITY = "DUPLICATE_ENTITY";
    public static final String CODE_ENTITY_IN_USE = "ENTITY_IN_USE";
    public static final String CODE_ILLEGAL_ARGUMENT = "ILLEGAL_ARGUMENT";
    public static final String CODE_BAD_REQUEST = "BAD_REQUEST";
    public static final String CODE_TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String CODE_DATA_INTEGRITY = "DATA_INTEGRITY_VIOLATION";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final AppProblemProperties problemProperties;
    private final MessageSource messageSource;

    private URI type(String suffix) {
        return URI.create(problemProperties.getBaseUri() + suffix);
    }

    /**
     * Resolve a localized message via Spring's {@link MessageSource} using the locale from
     * {@link LocaleContextHolder} (populated from the {@code Accept-Language} header by Spring's
     * default {@code AcceptHeaderLocaleResolver}). If the key is missing in the bundle, the key
     * itself is returned as the default — which makes missing translations obvious in QA without
     * crashing production. Keys live in {@code backend/src/main/resources/messages*.properties}
     * under the {@code problem.*.title} / {@code problem.*.detail} convention.
     */
    private String tr(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }

    private static ResponseEntity<ProblemDetail> toEntity(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /**
     * Resolve an existing correlation id from the SLF4J MDC or generate a fresh one.
     * Using MDC makes the same id appear in log lines emitted during the same request.
     */
    private static String correlationId() {
        String existing = MDC.get(MDC_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }

    /** Postgres / H2 / generic JDBC constraint name pattern (e.g. "violates foreign key constraint \"fk_xyz\""). */
    private static final Pattern CONSTRAINT_NAME_PATTERN = Pattern.compile("constraint\\s+\"?([A-Za-z0-9_]+)\"?");

    /**
     * Structured WARN log line shared by all 4xx handlers. Contains HTTP method, URI, status,
     * stable {@code code} and the per-request {@code correlationId} so that a user-reported
     * problem can be located in the log without a reproduction. The optional {@code extra}
     * map is appended verbatim and MUST NOT contain rejected user values (PII protection,
     * see AGENTS.md).
     */
    private void logProblem(@org.jetbrains.annotations.NotNull HttpServletRequest req,
                            @org.jetbrains.annotations.NotNull ProblemDetail pd,
                            @org.jetbrains.annotations.NotNull String shortMessage,
                            @org.jetbrains.annotations.Nullable Map<String, ?> extra) {
        Object cid = pd.getProperties() != null ? pd.getProperties().get("correlationId") : null;
        Object code = pd.getProperties() != null ? pd.getProperties().get("code") : null;
        if (extra == null || extra.isEmpty()) {
            log.warn("{} for {} {} [status={}, code={}, correlationId={}]",
                    shortMessage, req.getMethod(), req.getRequestURI(), pd.getStatus(), code, cid);
        } else {
            log.warn("{} for {} {} [status={}, code={}, correlationId={}] {}",
                    shortMessage, req.getMethod(), req.getRequestURI(), pd.getStatus(), code, cid, extra);
        }
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest req,
                                  String typeSuffix, String code) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(type(typeSuffix));
        if (req != null) {
            pd.setInstance(URI.create(req.getRequestURI()));
        }
        pd.setProperty("code", code);
        pd.setProperty("correlationId", correlationId());
        return pd;
    }

    // --- 409 Optimistic Locking -----------------------------------------------------------

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class,
            StaleObjectStateException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(Exception ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                tr("problem.optimistic_lock.title"),
                tr("problem.optimistic_lock.detail"),
                request,
                "concurrency-conflict",
                CODE_OPTIMISTIC_LOCK);
        logProblem(request, pd, "Optimistic locking conflict",
                Map.of("exception", ex.getClass().getSimpleName()));
        return toEntity(pd);
    }

    // --- 409 Pessimistic Locking ----------------------------------------------------------

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handlePessimisticLocking(PessimisticLockingFailureException ex,
                                                                  HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                tr("problem.pessimistic_lock.title"),
                tr("problem.pessimistic_lock.detail"),
                request,
                "pessimistic-lock",
                CODE_PESSIMISTIC_LOCK);
        logProblem(request, pd, "Pessimistic locking conflict",
                Map.of("exception", ex.getClass().getSimpleName()));
        return toEntity(pd);
    }

    // --- 401 Authentication ---------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.UNAUTHORIZED,
                tr("problem.unauthenticated.title"),
                tr("problem.unauthenticated.detail"),
                request,
                "unauthenticated",
                CODE_UNAUTHENTICATED);
        logProblem(request, pd, "Authentication required",
                Map.of("exception", ex.getClass().getSimpleName()));
        return toEntity(pd);
    }

    // --- 403 Authorization ----------------------------------------------------------------

    /**
     * Covers both the legacy {@link AccessDeniedException} and the newer Spring Security 6+
     * {@link AuthorizationDeniedException} raised by method security. The detail is kept
     * deliberately generic: the required role is not echoed back to avoid information
     * disclosure to clients who are already not authorized for this resource.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex, HttpServletRequest request) {
        // Do NOT echo ex.getMessage() to clients (may contain SpEL or required-role hints) -
        // keep the user-facing detail generic and put the diagnostic info into the WARN log only.
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                tr("problem.access_denied.title"),
                tr("problem.access_denied.detail"),
                request,
                "access-denied",
                CODE_ACCESS_DENIED);
        Map<String, Object> extra = new HashMap<>();
        extra.put("exception", ex.getClass().getSimpleName());
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            extra.put("reason", ex.getMessage());
        }
        if (ex instanceof AuthorizationDeniedException ade && ade.getAuthorizationResult() != null) {
            extra.put("authorizationResult", String.valueOf(ade.getAuthorizationResult()));
        }
        logProblem(request, pd, "Access denied", extra);
        return toEntity(pd);
    }

    // --- 400 Validation (@Valid on @RequestBody) ------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        Map<String, String> errorsByField = new HashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        List<String> logSummary = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            errorsByField.put(field, message);
            Map<String, String> entry = new HashMap<>();
            entry.put("field", field);
            entry.put("message", message);
            errors.add(entry);
            // Log only field name + violated constraint message; never the rejected value to
            // avoid leaking PII (handles, emails, recipient names) into the log files.
            logSummary.add(field + "=" + message + " (code=" + fieldError.getCode() + ")");
        });
        ex.getBindingResult().getGlobalErrors().forEach(globalError ->
                logSummary.add("[" + globalError.getObjectName() + "] " + globalError.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                tr("problem.validation_failed.title"),
                tr("problem.validation_failed.detail"),
                request,
                "constraint-violation",
                CODE_VALIDATION_FAILED);
        // Keep the legacy map-shaped "errors" for backwards compatibility AND expose a
        // structured list under "fieldErrors" for new consumers.
        pd.setProperty("errors", errorsByField);
        pd.setProperty("fieldErrors", errors);
        // WARN-level structured log so a 400 VALIDATION_FAILED can be analysed in production
        // without having to ask the user to reproduce the request (see CHANGELOG / log.txt L1467).
        log.warn("Validation failed for {} {} [correlationId={}]: {}",
                request.getMethod(), request.getRequestURI(),
                pd.getProperties() != null ? pd.getProperties().get("correlationId") : null,
                logSummary);
        return toEntity(pd);
    }

    // --- 400 Validation (@Validated on path/query params, jakarta constraints) ------------

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        List<String> logSummary = new ArrayList<>();
        ex.getConstraintViolations().forEach(v -> {
            String field = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "";
            Map<String, String> entry = new HashMap<>();
            entry.put("field", field);
            entry.put("message", v.getMessage());
            errors.add(entry);
            // Log field + message only; the invalid value may contain user input/PII.
            logSummary.add(field + "=" + v.getMessage());
        });
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                tr("problem.constraint_violation.title"),
                tr("problem.constraint_violation.detail"),
                request,
                "constraint-violation",
                CODE_CONSTRAINT_VIOLATION);
        pd.setProperty("fieldErrors", errors);
        log.warn("Constraint violation for {} {} [correlationId={}]: {}",
                request.getMethod(), request.getRequestURI(),
                pd.getProperties() != null ? pd.getProperties().get("correlationId") : null,
                logSummary);
        return toEntity(pd);
    }

    // --- 409 Domain conflicts -------------------------------------------------------------

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEntity(DuplicateEntityException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Duplicate entity",
                ex.getMessage(),
                request,
                "duplicate-entity",
                CODE_DUPLICATE_ENTITY);
        logProblem(request, pd, "Duplicate entity", null);
        return toEntity(pd);
    }

    @ExceptionHandler(EntityInUseException.class)
    public ResponseEntity<ProblemDetail> handleEntityInUse(EntityInUseException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Entity in use",
                ex.getMessage(),
                request,
                "entity-in-use",
                CODE_ENTITY_IN_USE);
        logProblem(request, pd, "Entity in use", null);
        return toEntity(pd);
    }

    // --- 400 Illegal arguments / malformed bodies -----------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        // ex.getMessage() can contain implementation details (SQL fragments, internal
        // paths, raw inputs that triggered a parser, ...). Return a generic detail to
        // the client and keep the real message only in the server log.
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                tr("problem.illegal_argument.title"),
                tr("problem.illegal_argument.detail"),
                request,
                "invalid-argument",
                CODE_ILLEGAL_ARGUMENT);
        logProblem(request, pd, "IllegalArgumentException",
                Map.of("exceptionMessage", String.valueOf(ex.getMessage())));
        return toEntity(pd);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = (ex.getStatusCode() instanceof HttpStatus hs) ? hs : HttpStatus.valueOf(ex.getStatusCode().value());
        String code = codeForStatus(status);
        ProblemDetail pd = problem(status,
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                ex.getMessage(),
                request,
                Integer.toString(status.value()),
                code);
        logProblem(request, pd, "ResponseStatusException",
                Map.of("reason", String.valueOf(ex.getReason())));
        return toEntity(pd);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponseException(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatus status = (ex.getStatusCode() instanceof HttpStatus hs) ? hs : HttpStatus.valueOf(ex.getStatusCode().value());
        ProblemDetail base = ex.getBody();
        if (base == null) {
            base = problem(status, status.getReasonPhrase(), ex.getMessage(), request,
                    Integer.toString(status.value()), codeForStatus(status));
        } else {
            base.setInstance(URI.create(request.getRequestURI()));
            if (base.getProperties() == null || !base.getProperties().containsKey("code")) {
                base.setProperty("code", codeForStatus(status));
            }
            if (base.getProperties() == null || !base.getProperties().containsKey("correlationId")) {
                base.setProperty("correlationId", correlationId());
            }
        }
        logProblem(request, base, "ErrorResponseException",
                Map.of("exception", ex.getClass().getSimpleName()));
        return toEntity(base);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Most-specific cause carries the actual JSON parse error (path, line, column) which is the
        // information needed to triage "400 BAD_REQUEST" reports without a reproduction.
        Throwable rootCause = ex.getMostSpecificCause();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                tr("problem.bad_request.title"),
                tr("problem.bad_request.detail"),
                request,
                "bad-request",
                CODE_BAD_REQUEST);
        Map<String, Object> extra = new HashMap<>();
        extra.put("contentType", String.valueOf(request.getContentType()));
        if (rootCause != null) {
            extra.put("rootCause", rootCause.getClass().getSimpleName());
            // Cause message may be long but does NOT contain raw user values (Jackson masks them);
            // it carries the JSON path/line/column needed for triage.
            extra.put("causeMessage", rootCause.getMessage());
            if (rootCause instanceof com.fasterxml.jackson.databind.JsonMappingException jme && jme.getPath() != null) {
                StringBuilder path = new StringBuilder();
                jme.getPath().forEach(ref -> {
                    if (ref.getFieldName() != null) {
                        if (path.length() > 0) {
                            path.append('.');
                        }
                        path.append(ref.getFieldName());
                    } else if (ref.getIndex() >= 0) {
                        path.append('[').append(ref.getIndex()).append(']');
                    }
                });
                if (path.length() > 0) {
                    extra.put("jsonPath", path.toString());
                }
            }
        }
        logProblem(request, pd, "Unreadable request body", extra);
        return toEntity(pd);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                tr("problem.data_integrity.title"),
                tr("problem.data_integrity.detail"),
                request,
                "data-integrity-violation",
                CODE_DATA_INTEGRITY);
        Throwable cause = ex.getMostSpecificCause();
        Map<String, Object> extra = new HashMap<>();
        if (cause != null) {
            extra.put("rootCause", cause.getClass().getSimpleName());
            String msg = cause.getMessage();
            if (msg != null) {
                Matcher m = CONSTRAINT_NAME_PATTERN.matcher(msg);
                if (m.find()) {
                    extra.put("constraint", m.group(1));
                }
                // First line of the cause message - usually the SQL state + constraint summary,
                // safe to log; subsequent lines may contain row data and are dropped.
                int nl = msg.indexOf('\n');
                extra.put("causeMessage", nl > 0 ? msg.substring(0, nl) : msg);
            }
        }
        logProblem(request, pd, "Data integrity violation", extra);
        return toEntity(pd);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                tr("problem.type_mismatch.title"),
                tr("problem.type_mismatch.detail", ex.getName()),
                request,
                "type-mismatch",
                CODE_TYPE_MISMATCH);
        // Do NOT log ex.getValue() - request parameter values may carry PII (handles, mails, IDs).
        Map<String, Object> extra = new HashMap<>();
        extra.put("parameter", ex.getName());
        extra.put("targetType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "n/a");
        logProblem(request, pd, "Type mismatch", extra);
        return toEntity(pd);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.METHOD_NOT_ALLOWED,
                tr("problem.method_not_allowed.title"),
                tr("problem.method_not_allowed.detail", ex.getMethod()),
                request,
                "method-not-allowed",
                CODE_METHOD_NOT_ALLOWED);
        logProblem(request, pd, "Method not allowed",
                Map.of("supportedMethods", String.valueOf(java.util.Arrays.toString(
                        ex.getSupportedMethods() == null ? new String[0] : ex.getSupportedMethods()))));
        return toEntity(pd);
    }

    // --- 404 Not Found --------------------------------------------------------------------

    /**
     * Handles both the application-specific {@link NotFoundException} as well as common JPA /
     * JDK flavors of "not found" ({@link EntityNotFoundException}, {@link NoSuchElementException})
     * and Spring's {@link NoResourceFoundException} (static resources / unknown paths) so that
     * none of them accidentally bubble up into the generic 500 handler.
     */
    @ExceptionHandler({NotFoundException.class, EntityNotFoundException.class,
            NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        // 404 is an expected, user-driven outcome (e.g. stale links, external crawlers hitting
        // deleted mission IDs). Log at DEBUG only and do NOT include the stacktrace to keep
        // the error log focused on real problems.
        log.debug("Not found at {}: {}", request.getRequestURI(), ex.getMessage());
        // Service-level NotFoundException messages are typically already i18n-resolved or domain
        // identifiers; fall back to a localized generic message only when no detail was provided.
        String detail = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : tr("problem.not_found.detail");
        ProblemDetail pd = problem(HttpStatus.NOT_FOUND,
                tr("problem.not_found.title"),
                detail,
                request,
                "not-found",
                CODE_NOT_FOUND);
        return toEntity(pd);
    }

    // --- 500 fallback ---------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, HttpServletRequest request) {
        String cid = correlationId();
        // Make sure the correlation id is the same for both the log line and the response.
        MDC.put(MDC_CORRELATION_ID, cid);
        try {
            log.error("Unexpected error at {} [correlationId={}]", request.getRequestURI(), cid, ex);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                tr("problem.internal_error.detail"));
        pd.setTitle(tr("problem.internal_error.title"));
        pd.setType(type("internal-error"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", CODE_INTERNAL_ERROR);
        pd.setProperty("correlationId", cid);
        return toEntity(pd);
    }

    /**
     * Map an arbitrary HTTP status to a reasonable default error code. Used for
     * {@link ResponseStatusException} / {@link ErrorResponseException} where the original
     * cause is not known to this handler.
     */
    private static String codeForStatus(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> CODE_UNAUTHENTICATED;
            case FORBIDDEN -> CODE_ACCESS_DENIED;
            case NOT_FOUND -> CODE_NOT_FOUND;
            case CONFLICT -> CODE_DUPLICATE_ENTITY;
            case METHOD_NOT_ALLOWED -> CODE_METHOD_NOT_ALLOWED;
            case BAD_REQUEST -> CODE_BAD_REQUEST;
            default -> status.is5xxServerError() ? CODE_INTERNAL_ERROR : CODE_BAD_REQUEST;
        };
    }
}
