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

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.NotificationBulkResultDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.NotificationCountResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.NotificationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.NotificationViewDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Frontend page + AJAX relay for the per-user notification inbox. The browser never talks to the
 * backend directly: this controller proxies to the backend REST API (which derives the recipient
 * from the session's JWT) and localizes each notification's text server-side via {@link
 * MessageSource} so the page and the bell dropdown render identical strings.
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class NotificationPageController {

  private static final String BACKEND_BASE = "/api/v1/notifications";
  private static final int PAGE_LIMIT = 50;
  private static final int DROPDOWN_LIMIT = 10;
  private static final DateTimeFormatter DISPLAY_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
  private static final ParameterizedTypeReference<List<NotificationDto>> LIST_TYPE =
      new ParameterizedTypeReference<>() {};

  private final BackendApiClient backendApiClient;
  private final MessageSource messageSource;

  /**
   * Renders the full notifications page (most recent first), fail-soft to an empty list.
   *
   * @param model the view model
   * @return the notifications template name
   */
  @GetMapping
  public String page(Model model) {
    try {
      model.addAttribute("notifications", loadView(PAGE_LIMIT));
    } catch (Exception e) {
      log.debug("Failed to load notifications page", e);
      model.addAttribute("notifications", List.of());
      model.addAttribute("error", "notifications.error.load");
    }
    return "notifications";
  }

  /**
   * Returns the most recent notifications for the bell dropdown as JSON.
   *
   * @return the localized notification view DTOs
   */
  @ResponseBody
  @GetMapping(value = "/recent", headers = "X-Requested-With=XMLHttpRequest")
  public List<NotificationViewDto> recent() {
    return loadView(DROPDOWN_LIMIT);
  }

  /**
   * Returns the caller's unread count for the always-on badge poll.
   *
   * @return the unread count payload (fail-soft to zero on a backend hiccup)
   */
  @ResponseBody
  @GetMapping(value = "/unread-count", headers = "X-Requested-With=XMLHttpRequest")
  public NotificationCountResponse unreadCount() {
    return new NotificationCountResponse(currentUnreadCount());
  }

  /**
   * Marks one notification read (AJAX relay).
   *
   * @param id notification id
   * @return 200 on success, or the relayed backend error
   */
  @ResponseBody
  @PostMapping(value = "/{id}/read", headers = "X-Requested-With=XMLHttpRequest")
  public ResponseEntity<Object> markRead(@PathVariable @NotNull UUID id) {
    try {
      backendApiClient.post(BACKEND_BASE + "/" + id + "/read", null, NotificationDto.class);
      return ResponseEntity.ok(new NotificationCountResponse(currentUnreadCount()));
    } catch (BackendServiceException e) {
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Mark-read {} (ajax) failed", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Marks all of the caller's notifications read (AJAX relay).
   *
   * @return the bulk result, or the relayed backend error
   */
  @ResponseBody
  @PostMapping(value = "/read-all", headers = "X-Requested-With=XMLHttpRequest")
  public ResponseEntity<Object> markAllRead() {
    try {
      NotificationBulkResultDto result =
          backendApiClient.post(BACKEND_BASE + "/read-all", null, NotificationBulkResultDto.class);
      return ResponseEntity.ok(result);
    } catch (BackendServiceException e) {
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Mark-all-read (ajax) failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Deletes one notification, read or unread (AJAX relay).
   *
   * @param id notification id
   * @return the resulting unread count, or the relayed backend error
   */
  @ResponseBody
  @DeleteMapping(value = "/{id}", headers = "X-Requested-With=XMLHttpRequest")
  public ResponseEntity<Object> delete(@PathVariable @NotNull UUID id) {
    try {
      backendApiClient.delete(BACKEND_BASE + "/" + id, Void.class);
      return ResponseEntity.ok(new NotificationCountResponse(currentUnreadCount()));
    } catch (BackendServiceException e) {
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Delete notification {} (ajax) failed", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Deletes all of the caller's already-read notifications (AJAX relay).
   *
   * @return the bulk result, or the relayed backend error
   */
  @ResponseBody
  @DeleteMapping(value = "/read", headers = "X-Requested-With=XMLHttpRequest")
  public ResponseEntity<Object> clearRead() {
    try {
      NotificationBulkResultDto result =
          backendApiClient.delete(BACKEND_BASE + "/read", NotificationBulkResultDto.class);
      return ResponseEntity.ok(result);
    } catch (BackendServiceException e) {
      return propagateBackendError(e);
    } catch (Exception e) {
      log.error("Clear-read (ajax) failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private List<NotificationViewDto> loadView(int limit) {
    List<NotificationDto> dtos =
        backendApiClient.get(
            BACKEND_BASE + "/recent?limit={limit}", LIST_TYPE, Integer.valueOf(limit));
    if (dtos == null) {
      return List.of();
    }
    Locale locale = LocaleContextHolder.getLocale();
    return dtos.stream().map(dto -> toView(dto, locale)).toList();
  }

  private NotificationViewDto toView(NotificationDto dto, Locale locale) {
    return new NotificationViewDto(
        dto.id(),
        render(dto.type(), dto.params(), locale),
        dto.read(),
        dto.createdAt() == null ? "" : DISPLAY_FORMAT.format(dto.createdAt()),
        dto.entityType(),
        dto.entityId());
  }

  private String render(String type, Map<String, String> params, Locale locale) {
    String key = "notifications.type." + type;
    String template = messageSource.getMessage(key, null, key, locale);
    if (template == null || template.equals(key)) {
      template =
          messageSource.getMessage("notifications.type.generic", null, "Notification", locale);
    }
    if (params != null) {
      for (Map.Entry<String, String> entry : params.entrySet()) {
        template =
            template.replace(
                "{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
      }
    }
    return template;
  }

  private long currentUnreadCount() {
    try {
      NotificationCountResponse response =
          backendApiClient.get(BACKEND_BASE + "/unread-count", NotificationCountResponse.class);
      return response != null && response.count() != null ? response.count() : 0L;
    } catch (Exception e) {
      log.debug("Failed to load unread count", e);
      return 0L;
    }
  }

  private static ResponseEntity<Object> propagateBackendError(BackendServiceException e) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", e.getStatusCode());
    body.put("code", e.getProblemCode());
    if (e.getProblemDetail() != null && !e.getProblemDetail().isBlank()) {
      body.put("detail", e.getProblemDetail());
    }
    if (e.getCorrelationId() != null && !e.getCorrelationId().isBlank()) {
      body.put("correlationId", e.getCorrelationId());
    }
    return ResponseEntity.status(e.getStatusCode())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }
}
