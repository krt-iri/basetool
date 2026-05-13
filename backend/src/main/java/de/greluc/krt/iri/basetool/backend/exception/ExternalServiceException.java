package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Thrown when an upstream service the backend depends on (Keycloak, UEX, …) returns an error
 * response or is unreachable.
 *
 * <p>Mapped to HTTP {@code 502 Bad Gateway} by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable error code
 * {@code EXTERNAL_SERVICE_ERROR}. The original cause is kept on the exception for server-side
 * logging; the {@code detail} body returned to the client is a generic localized message so we do
 * not echo back implementation details (status codes, response bodies) of the upstream service to
 * the caller.
 *
 * <p>Prefer this over a plain {@link RuntimeException} so an upstream outage surfaces as a clearly
 * distinguishable 5xx category in the client and in the logs, rather than being indistinguishable
 * from an unexpected internal bug ({@code 500 INTERNAL_ERROR}).
 */
public class ExternalServiceException extends RuntimeException {

  public ExternalServiceException(String message) {
    super(message);
  }

  public ExternalServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
