/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.websocket;

import de.greluc.krt.profit.basetool.frontend.service.MissionPresenceService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Native WebSocket handler for the mission-detail presence/awareness feature.
 *
 * <p>Each authenticated browser tab opens one socket against {@code
 * /ws/missions/{missionId}/presence}. The handler:
 *
 * <ul>
 *   <li>extracts the mission id from the URI and the user identity from the {@link Principal}
 *       attached to the WebSocket session by Spring Security;
 *   <li>receives JSON messages from the client of the form {@code {"type":"focus"|"blur"
 *       |"heartbeat", "sectionKey":"..."}} and updates the {@link MissionPresenceService};
 *   <li>after every mutation broadcasts the full snapshot for that mission to every connected
 *       socket on the same mission so all clients converge on the same indicator state;
 *   <li>runs a scheduled reaper at {@link #REAPER_INTERVAL} that drops entries past TTL and
 *       broadcasts a fresh snapshot to the affected rooms.
 * </ul>
 *
 * <p>The wire format is intentionally minimal — no STOMP, no SockJS. The server never pushes
 * unsolicited messages other than presence snapshots.
 *
 * <p><b>Concurrency:</b> the per-mission session map is a {@link ConcurrentHashMap}, but broadcasts
 * iterate over a defensive copy so that a slow consumer's {@link WebSocketSession#sendMessage} call
 * cannot block other broadcasts. Individual session writes are serialised via {@code
 * synchronized(session)} as required by the Spring WebSocket contract.
 */
@Slf4j
public class MissionPresenceWebSocketHandler extends TextWebSocketHandler {

  /** How often the reaper runs to drop expired presence entries and broadcast updates. */
  public static final Duration REAPER_INTERVAL = Duration.ofSeconds(10);

  private static final String ATTR_MISSION_ID = "missionPresence.missionId";
  private static final String ATTR_USER_ID = "missionPresence.userId";
  private static final String ATTR_DISPLAY_NAME = "missionPresence.displayName";

  private final MissionPresenceService presenceService;
  private final ObjectMapper objectMapper;
  private final ScheduledExecutorService reaper;

  private final Map<UUID, Set<WebSocketSession>> sessionsByMission = new ConcurrentHashMap<>();

  /**
   * Builds the handler. Spring registers it as a bean (see {@code MissionPresenceWebSocketConfig})
   * and the reaper starts ticking immediately.
   *
   * @param presenceService in-memory presence store
   * @param objectMapper Jackson mapper, shared with the rest of the app
   */
  public MissionPresenceWebSocketHandler(
      @NotNull MissionPresenceService presenceService, @NotNull ObjectMapper objectMapper) {
    this.presenceService = presenceService;
    this.objectMapper = objectMapper;
    this.reaper =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mission-presence-reaper");
              t.setDaemon(true);
              return t;
            });
    this.reaper.scheduleAtFixedRate(
        this::tickReaper,
        REAPER_INTERVAL.toSeconds(),
        REAPER_INTERVAL.toSeconds(),
        TimeUnit.SECONDS);
  }

  /** Shuts the reaper thread down cleanly on application shutdown. */
  @PreDestroy
  public void shutdown() {
    reaper.shutdownNow();
  }

  /**
   * Called by Spring after a successful WebSocket handshake. Extracts the mission id from the path
   * and the user identity from the principal; rejects sockets that fail either check.
   *
   * @param session the freshly opened session
   */
  @Override
  public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
    UUID missionId = extractMissionId(session.getUri());
    Principal principal = session.getPrincipal();
    if (missionId == null || principal == null) {
      log.debug(
          "Presence socket refused (missionId={}, hasPrincipal={})", missionId, principal != null);
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    String userId = resolveUserId(principal);
    if (userId == null) {
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    String displayName = resolveDisplayName(principal);
    session.getAttributes().put(ATTR_MISSION_ID, missionId);
    session.getAttributes().put(ATTR_USER_ID, userId);
    session.getAttributes().put(ATTR_DISPLAY_NAME, displayName);
    sessionsByMission
        .computeIfAbsent(missionId, ignored -> ConcurrentHashMap.newKeySet())
        .add(session);
    sendSnapshot(session, missionId);
  }

  /**
   * Parses a single client message and applies it to the presence store. Unknown message types are
   * silently ignored to keep wire-format evolution forward-compatible.
   *
   * @param session the session that produced the message
   * @param message the text payload
   */
  @Override
  protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message)
      throws Exception {
    UUID missionId = (UUID) session.getAttributes().get(ATTR_MISSION_ID);
    String userId = (String) session.getAttributes().get(ATTR_USER_ID);
    if (missionId == null || userId == null) {
      return;
    }

    JsonNode node;
    try {
      node = objectMapper.readTree(message.getPayload());
    } catch (JacksonException e) {
      log.debug("Discarding malformed presence message", e);
      return;
    }
    String type = textValue(node, "type");
    String sectionKey = textValue(node, "sectionKey");
    if (type == null || sectionKey == null || sectionKey.isBlank()) {
      return;
    }

    boolean mutated;
    switch (type) {
      case "focus", "heartbeat" -> {
        String displayName = (String) session.getAttributes().get(ATTR_DISPLAY_NAME);
        mutated = presenceService.touch(missionId, sectionKey, userId, displayName);
      }
      case "blur" -> mutated = presenceService.clear(missionId, sectionKey, userId);
      default -> {
        return;
      }
    }
    // Always broadcast on focus/blur (state changes); on heartbeat broadcast only when this is the
    // FIRST sighting of the editor (touch returns true) — otherwise the snapshot is identical and
    // the broadcast would just generate noise.
    if (mutated || "blur".equals(type) || "focus".equals(type)) {
      broadcastSnapshot(missionId);
    }
  }

  /**
   * Cleans up the closed session — drops every presence entry the user had on this mission and
   * broadcasts the resulting state so other clients see the indicator disappear.
   *
   * @param session the closing session
   * @param status close reason (unused; logged for diagnostics)
   */
  @Override
  public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status)
      throws Exception {
    UUID missionId = (UUID) session.getAttributes().get(ATTR_MISSION_ID);
    String userId = (String) session.getAttributes().get(ATTR_USER_ID);
    if (missionId == null || userId == null) {
      return;
    }
    Set<WebSocketSession> mates = sessionsByMission.get(missionId);
    if (mates != null) {
      mates.remove(session);
      if (mates.isEmpty()) {
        sessionsByMission.remove(missionId, mates);
      }
    }
    // Only clear the user from presence if they have no OTHER live sessions on the same mission
    // (multiple tabs from the same browser would otherwise wipe each other's heartbeats).
    boolean hasOtherSession =
        mates != null
            && mates.stream().anyMatch(s -> userId.equals(s.getAttributes().get(ATTR_USER_ID)));
    if (!hasOtherSession) {
      List<String> cleared = presenceService.clearAll(missionId, userId);
      if (!cleared.isEmpty()) {
        broadcastSnapshot(missionId);
      }
    }
  }

  /**
   * Reaper tick — drops expired entries from every tracked mission and broadcasts the resulting
   * snapshot to rooms that lost at least one entry. Runs on a single daemon thread; any thrown
   * exception is logged and swallowed so a transient failure does not kill the reaper.
   */
  void tickReaper() {
    try {
      List<MissionPresenceService.MissionSectionRef> affected =
          presenceService.reapExpired(Instant.now());
      if (affected.isEmpty()) {
        return;
      }
      Set<UUID> uniqueMissions = new HashSet<>();
      for (MissionPresenceService.MissionSectionRef ref : affected) {
        uniqueMissions.add(ref.missionId());
      }
      for (UUID missionId : uniqueMissions) {
        broadcastSnapshot(missionId);
      }
    } catch (RuntimeException e) {
      log.warn("Presence reaper tick failed", e);
    }
  }

  private void broadcastSnapshot(@NotNull UUID missionId) {
    Set<WebSocketSession> mates = sessionsByMission.get(missionId);
    if (mates == null || mates.isEmpty()) {
      return;
    }
    String payload;
    try {
      payload = objectMapper.writeValueAsString(buildSnapshot(missionId));
    } catch (JacksonException e) {
      log.warn("Failed to serialise presence snapshot for mission {}", missionId, e);
      return;
    }
    TextMessage message = new TextMessage(payload);
    for (WebSocketSession session : List.copyOf(mates)) {
      sendSafe(session, message);
    }
  }

  private void sendSnapshot(@NotNull WebSocketSession session, @NotNull UUID missionId) {
    try {
      String payload = objectMapper.writeValueAsString(buildSnapshot(missionId));
      sendSafe(session, new TextMessage(payload));
    } catch (JacksonException e) {
      log.warn("Failed to serialise initial presence snapshot for mission {}", missionId, e);
    }
  }

  private ObjectNode buildSnapshot(@NotNull UUID missionId) {
    Map<String, List<MissionPresenceService.Entry>> snapshot =
        presenceService.snapshot(missionId, Instant.now());
    ObjectNode root = objectMapper.createObjectNode();
    root.put("type", "presence");
    ObjectNode sections = root.putObject("sections");
    for (Map.Entry<String, List<MissionPresenceService.Entry>> e : snapshot.entrySet()) {
      ArrayNode editors = sections.putArray(e.getKey());
      for (MissionPresenceService.Entry editor : e.getValue()) {
        ObjectNode editorNode = editors.addObject();
        editorNode.put("userId", editor.userId());
        editorNode.put("displayName", editor.displayName());
      }
    }
    return root;
  }

  private void sendSafe(@NotNull WebSocketSession session, @NotNull TextMessage message) {
    if (!session.isOpen()) {
      return;
    }
    // Spring WebSocket forbids concurrent sends on a single session; serialise here.
    synchronized (session) {
      try {
        session.sendMessage(message);
      } catch (IOException | IllegalStateException e) {
        log.debug("Drop presence frame to closed/broken session {}", session.getId(), e);
      }
    }
  }

  static UUID extractMissionId(URI uri) {
    if (uri == null) {
      return null;
    }
    String path = uri.getPath();
    if (path == null) {
      return null;
    }
    // Expected: /ws/missions/{uuid}/presence
    String[] parts = path.split("/");
    List<String> nonEmpty = new ArrayList<>(parts.length);
    for (String p : parts) {
      if (!p.isEmpty()) {
        nonEmpty.add(p);
      }
    }
    if (nonEmpty.size() != 4
        || !"ws".equals(nonEmpty.get(0))
        || !"missions".equals(nonEmpty.get(1))
        || !"presence".equals(nonEmpty.get(3))) {
      return null;
    }
    try {
      return UUID.fromString(nonEmpty.get(2));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String resolveUserId(@NotNull Principal principal) {
    if (principal
        instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
      Object p = token.getPrincipal();
      if (p instanceof OidcUser oidc && oidc.getSubject() != null && !oidc.getSubject().isBlank()) {
        return oidc.getSubject();
      }
    }
    String name = principal.getName();
    return (name == null || name.isBlank()) ? null : name;
  }

  private static String resolveDisplayName(@NotNull Principal principal) {
    if (principal
        instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
      Object p = token.getPrincipal();
      if (p instanceof OidcUser oidc) {
        // Privacy / data minimisation: the presence label is derived from the public callsign
        // (preferred_username) only. given_name / family_name / the composite name claim are no
        // longer read here — those claims are removed from the Keycloak tokens.
        String preferred = oidc.getPreferredUsername();
        if (preferred != null && !preferred.isBlank()) {
          return preferred;
        }
      }
    }
    String name = principal.getName();
    return name == null ? "" : name;
  }

  private static String textValue(@NotNull JsonNode node, @NotNull String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString()) {
      return null;
    }
    String s = value.asString();
    return Objects.equals(s, "") ? null : s;
  }
}
