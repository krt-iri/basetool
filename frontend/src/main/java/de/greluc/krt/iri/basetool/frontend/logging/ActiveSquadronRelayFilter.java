package de.greluc.krt.iri.basetool.frontend.logging;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Relays the admin's active squadron selection from the frontend's Spring Session to the backend
 * via the {@code X-Active-Squadron-Id} request header on every outbound {@code WebClient} call.
 *
 * <p>The state lives on the frontend because backend REST calls do not relay session cookies (the
 * frontend's {@code BackendApiClient} only attaches the OAuth2 bearer token), so a backend-side
 * {@code HttpSession} would be lost between calls. The active squadron is snapshotted onto a
 * thread-local by {@link ActiveSquadronContextFilter} at the start of every servlet request and
 * read here on the WebClient pipeline. Reactor's automatic context propagation (enabled by Spring
 * Boot 4) carries the thread-local across the hop to the Netty reactor thread that actually issues
 * the I/O.
 *
 * <p>Failure modes degrade silently: no thread-local bound (background task / scheduled job) and no
 * active squadron set both yield "no header added" — the backend then falls through to its default
 * behaviour (admin sees all squadrons, members see their home squadron).
 */
@Component
public class ActiveSquadronRelayFilter {

  /** HTTP header name carrying the admin's active squadron selection to the backend. */
  public static final String ACTIVE_SQUADRON_HEADER = "X-Active-Squadron-Id";

  /**
   * Returns the filter function that adds the {@code X-Active-Squadron-Id} header to outbound
   * requests when an admin has a squadron selected in the frontend session. No header is added for
   * non-admin users (their effective squadron is resolved by the backend from {@code
   * app_user.squadron_id}) or for admins in "all squadrons" mode.
   *
   * @return filter function for the WebClient pipeline; never {@code null}.
   */
  @NotNull
  public ExchangeFilterFunction relayActiveSquadron() {
    return (request, next) -> {
      UUID active = ActiveSquadronContext.get();
      if (active == null) {
        return next.exchange(request);
      }
      return next.exchange(
          ClientRequest.from(request).header(ACTIVE_SQUADRON_HEADER, active.toString()).build());
    };
  }
}
