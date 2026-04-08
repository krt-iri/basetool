package de.greluc.krt.iri.basetool.backend.exception;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppProblemProperties problemProperties;

    private URI type(String suffix) {
        return URI.create(problemProperties.getBaseUri() + suffix);
    }

    private static ResponseEntity<ProblemDetail> toEntity(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest req, URI type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        if (type != null) pd.setType(type);
        if (req != null) pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Concurrency conflict",
                "The resource has been updated by another user. Please reload and try again.",
                request,
                type("concurrency-conflict"));
        return toEntity(pd);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN,
                "Access denied",
                ex.getMessage() != null ? ex.getMessage() : "Access is denied",
                request,
                type("access-denied"));
        return toEntity(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more fields have invalid values.",
                request,
                type("constraint-violation"));
        pd.setProperty("errors", errors);
        return toEntity(pd);
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEntity(DuplicateEntityException ex, HttpServletRequest request) {
        log.warn("Duplicate entity: {}", ex.getMessage());
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Duplicate entity",
                ex.getMessage(),
                request,
                type("duplicate-entity"));
        return toEntity(pd);
    }

    @ExceptionHandler(EntityInUseException.class)
    public ResponseEntity<ProblemDetail> handleEntityInUse(EntityInUseException ex, HttpServletRequest request) {
        log.warn("Entity in use: {}", ex.getMessage());
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Entity in use",
                ex.getMessage(),
                request,
                type("entity-in-use"));
        return toEntity(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[DEBUG_LOG] IllegalArgumentException at {}: {}", request.getRequestURI(), ex.getMessage());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Invalid argument",
                ex.getMessage(),
                request,
                type("invalid-argument"));
        return toEntity(pd);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = (ex.getStatusCode() instanceof HttpStatus hs) ? hs : HttpStatus.valueOf(ex.getStatusCode().value());
        ProblemDetail pd = problem(status,
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                ex.getMessage(),
                request,
                type("" + status.value()));
        return toEntity(pd);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponseException(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatus status = (ex.getStatusCode() instanceof HttpStatus hs) ? hs : HttpStatus.valueOf(ex.getStatusCode().value());
        ProblemDetail base = ex.getBody();
        if (base == null) {
            base = problem(status, status.getReasonPhrase(), ex.getMessage(), request, type("" + status.value()));
        } else {
            base.setInstance(URI.create(request.getRequestURI()));
        }
        return toEntity(base);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.error("HttpMessageNotReadableException: ", ex);
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Failed to read request",
                request,
                type("bad-request"));
        return toEntity(pd);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: ", ex);
        ProblemDetail pd = problem(HttpStatus.CONFLICT,
                "Data integrity violation",
                "The operation could not be completed due to a database constraint violation.",
                request,
                type("data-integrity-violation"));
        return toEntity(pd);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("[DEBUG_LOG] Type mismatch for parameter {}: value='{}', targetType={}, message={}", 
            ex.getName(), ex.getValue(), ex.getRequiredType(), ex.getMessage());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
                "Type mismatch",
                "Invalid value for parameter " + ex.getName(),
                request,
                type("type-mismatch"));
        return toEntity(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("An unexpected error occurred", ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please contact the administrator.",
                request,
                type("internal-error"));
        return toEntity(pd);
    }
}
