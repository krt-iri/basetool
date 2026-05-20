package de.greluc.krt.iri.basetool.frontend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.frontend.service.MissionPresenceService;
import de.greluc.krt.iri.basetool.frontend.websocket.MissionPresenceWebSocketHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Wires the mission-detail presence WebSocket endpoint.
 *
 * <p>Exposes {@code /ws/missions/{missionId}/presence} as a native Spring WebSocket endpoint. The
 * mission id is path-templated so a single handler instance serves every mission; per-mission
 * session sets live inside the handler.
 *
 * <p>The WebSocket handshake is gated by an explicit {@code setAllowedOriginPatterns} list (driven
 * by {@code app.websocket.allowed-origin-patterns}) — {@code setAllowedOriginPatterns("*")} would
 * leave the door open for Cross-Site WebSocket Hijacking even though the Spring Security chain in
 * {@link SecurityConfig} already requires authentication: the browser would still ship the victim's
 * session cookie on the upgrade request, and the handshake would succeed as the victim for an
 * attacker page on a third-party origin. Default falls back to the production hostname ({@code
 * https://iri-base.org}) plus localhost variants for dev (audit finding H-7).
 *
 * <p>The handler uses its own {@link ObjectMapper} rather than the Spring-auto-configured bean:
 * Spring Boot 4 has moved its primary mapper to Jackson 3 ({@code tools.jackson.core}), but this
 * presence feature still talks Jackson 2 ({@code com.fasterxml}) — the same trade-off the
 * Thymeleaf-JS-serialiser already documents in the frontend's {@code build.gradle.kts}.
 */
@Configuration
@EnableWebSocket
public class MissionPresenceWebSocketConfig implements WebSocketConfigurer {

  private final MissionPresenceService presenceService;
  private final List<String> allowedOriginPatterns;

  /**
   * Constructor injection of the shared presence store and the WebSocket origin allowlist.
   *
   * @param presenceService in-memory presence store
   * @param allowedOriginPatterns origin patterns accepted on the WebSocket handshake; sourced from
   *     {@code app.websocket.allowed-origin-patterns} with a production default
   */
  public MissionPresenceWebSocketConfig(
      MissionPresenceService presenceService,
      @Value(
              "${app.websocket.allowed-origin-patterns:https://iri-base.org,https://localhost:18081,http://localhost:18081}")
          List<String> allowedOriginPatterns) {
    this.presenceService = presenceService;
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  /**
   * Builds the singleton {@link MissionPresenceWebSocketHandler}. Declared as a bean so Spring
   * triggers its {@code @PreDestroy} on shutdown.
   *
   * @return the handler bean
   */
  @Bean
  public MissionPresenceWebSocketHandler missionPresenceWebSocketHandler() {
    return new MissionPresenceWebSocketHandler(presenceService, new ObjectMapper());
  }

  /** {@inheritDoc} */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(missionPresenceWebSocketHandler(), "/ws/missions/{missionId}/presence")
        .setAllowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]));
  }
}
