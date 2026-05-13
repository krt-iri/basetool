package de.greluc.krt.iri.basetool.frontend.logging;

import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holder for the current request's correlation id, so that non-servlet components
 * (e.g. the {@code WebClientLoggingFilter}) can propagate the id towards the backend without
 * re-reading the original {@code HttpServletRequest}. Populated by {@link CorrelationIdFilter} at
 * the beginning of every request and cleared in the matching {@code finally} block.
 *
 * <p>Virtual threads and reactive pipelines copy the owning thread's thread-locals on submission,
 * so the value is visible inside {@code WebClient} exchange filters that run on the request thread.
 * Calls escaping to a different scheduler must pass the id explicitly.
 */
public final class CorrelationContext {

  private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

  private CorrelationContext() {
    // utility
  }

  /** Stores the given correlation id in the calling thread; a blank value clears the slot. */
  public static void set(@Nullable String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      HOLDER.remove();
    } else {
      HOLDER.set(correlationId);
    }
  }

  /** Returns the correlation id stored for the current thread, or {@code null} if none set. */
  @Nullable public static String get() {
    return HOLDER.get();
  }

  /** Removes the stored correlation id - call from {@code finally} blocks to avoid leakage. */
  public static void clear() {
    HOLDER.remove();
  }
}
