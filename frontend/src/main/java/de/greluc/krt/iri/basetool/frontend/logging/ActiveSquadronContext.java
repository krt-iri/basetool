package de.greluc.krt.iri.basetool.frontend.logging;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holder for the admin's currently selected squadron, so that the WebClient exchange
 * filter ({@code ActiveSquadronRelayFilter}) can attach the {@code X-Active-Squadron-Id} header
 * without re-reading the {@code HttpSession} from a Netty reactor thread that does not have the
 * Tomcat request bound to its {@code RequestContextHolder}.
 *
 * <p>Populated by {@code ActiveSquadronContextFilter} at the beginning of every servlet request
 * (snapshotted from the frontend's Spring Session) and cleared in the matching {@code finally}
 * block to avoid bleed-through on pooled / virtual threads. Mirrors the {@link CorrelationContext}
 * pattern; Reactor's automatic context propagation (enabled by Spring Boot 4) carries the
 * thread-local to the WebClient exchange filter even when the actual I/O runs on a Netty epoll
 * thread.
 */
public final class ActiveSquadronContext {

  private static final ThreadLocal<UUID> HOLDER = new ThreadLocal<>();

  private ActiveSquadronContext() {
    // utility
  }

  /**
   * Stores the given squadron id in the calling thread; a {@code null} value clears the slot.
   *
   * @param squadronId active squadron UUID, or {@code null} to clear.
   */
  public static void set(@Nullable UUID squadronId) {
    if (squadronId == null) {
      HOLDER.remove();
    } else {
      HOLDER.set(squadronId);
    }
  }

  /**
   * Returns the squadron id stored for the current thread.
   *
   * @return active squadron UUID, or {@code null} when none is bound.
   */
  @Nullable
  public static UUID get() {
    return HOLDER.get();
  }

  /** Removes the stored squadron id - call from {@code finally} blocks to avoid leakage. */
  public static void clear() {
    HOLDER.remove();
  }

  /**
   * Coerces a raw session-attribute value into a {@link UUID}. Spring Session backed by Redis can
   * round-trip a UUID instance as a String depending on the configured serializer, so the read-side
   * accepts either type. Returns {@code null} for anything that does not parse cleanly, matching
   * the "no active selection" fall-through behaviour the rest of the code expects.
   *
   * @param raw value pulled from the session attribute store; may be a {@link UUID}, a {@link
   *     String}, or {@code null}.
   * @return parsed UUID, or {@code null} when the input is missing or malformed.
   */
  @Nullable
  public static UUID coerce(@Nullable Object raw) {
    if (raw instanceof UUID uuid) {
      return uuid;
    }
    if (raw instanceof String s && !s.isBlank()) {
      try {
        return UUID.fromString(s.trim());
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }
}
