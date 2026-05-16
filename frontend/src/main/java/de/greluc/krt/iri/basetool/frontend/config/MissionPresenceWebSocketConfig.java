package de.greluc.krt.iri.basetool.frontend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.frontend.service.MissionPresenceService;
import de.greluc.krt.iri.basetool.frontend.websocket.MissionPresenceWebSocketHandler;
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
 * <p>{@code setAllowedOriginPatterns("*")} is safe here because the Spring Security chain in {@link
 * SecurityConfig} already restricts {@code /ws/**} to authenticated callers — anonymous
 * cross-origin upgrades are blocked before they reach this handler.
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

  /**
   * Constructor injection of the shared presence store.
   *
   * @param presenceService in-memory presence store
   */
  public MissionPresenceWebSocketConfig(MissionPresenceService presenceService) {
    this.presenceService = presenceService;
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
        .setAllowedOriginPatterns("*");
  }
}
