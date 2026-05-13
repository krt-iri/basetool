package de.greluc.krt.iri.basetool.backend.exception;

/**
 * Thrown when generating a downloadable report (PDF, CSV, …) fails because of an unexpected problem
 * in the report pipeline — typically an {@code IOException} from the PDF library, a missing
 * required field on the input model, or an unsupported font/encoding.
 *
 * <p>Mapped to HTTP {@code 500 Internal Server Error} by {@link
 * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler} with the stable error code
 * {@code REPORT_GENERATION_FAILED}. Compared to the generic 500 fallback this gives
 * monitoring/alerting a dedicated handle on a specific failure mode (a report problem is rarely a
 * code bug, but it is also not a user-input problem in the sense of {@code BadRequestException}).
 *
 * <p>The original cause is preserved so the server log shows the full stack trace; the client
 * receives a localized generic detail instead of the raw upstream message.
 */
public class ReportGenerationException extends RuntimeException {

  public ReportGenerationException(String message) {
    super(message);
  }

  public ReportGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
