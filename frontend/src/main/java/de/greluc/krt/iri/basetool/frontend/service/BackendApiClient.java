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

package de.greluc.krt.iri.basetool.frontend.service;

import de.greluc.krt.iri.basetool.frontend.config.CacheConfig;
import de.greluc.krt.iri.basetool.frontend.exception.ReauthenticationRequiredException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * WebClient wrapper for the backend REST API. Centralises RFC-7807 problem-response parsing into
 * {@link BackendServiceException} and exposes typed convenience overloads for every HTTP verb.
 * {@code getCached(...)} layers Spring Cache on top using {@link CacheConfig#STATIC_DATA_CACHE}.
 *
 * <p><b>Resilience layering.</b> Every outbound call — regardless of HTTP verb — already passes
 * through the {@link de.greluc.krt.iri.basetool.frontend.config.WebClientConfig#resilienceFilter
 * WebClient filter chain}, which applies the operators bulkhead → time limiter → retry (only on
 * idempotent verbs GET/HEAD/OPTIONS/TRACE, never on writes) → circuit breaker against the {@code
 * backendApi} Resilience4j instance. The filter-level {@link
 * io.github.resilience4j.timelimiter.TimeLimiter} therefore covers POST/PUT/PATCH/DELETE the same
 * way it covers GET — there is no timeout gap on state-changing calls. The method-level
 * {@code @Retry}/{@code @CircuitBreaker} annotations below layer a second, AOP-level resilience
 * pass on top, on the separate {@code backend} Resilience4j instance, so the call is wrapped twice;
 * the filter alone is what guards against a hanging upstream thread.
 *
 * <p>Page controllers should call into this client and let {@link
 * de.greluc.krt.iri.basetool.frontend.exception.GlobalExceptionHandler} surface failures — do not
 * catch {@link BackendServiceException} on the call site.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackendApiClient {

  private final WebClient webClient;
  private final WebClient publicWebClient;

  /**
   * Dedicated ObjectMapper used exclusively to decode backend Problem+JSON bodies. Kept as an
   * internal instance — not auto-wired — because some frontend test slices run without Spring
   * Boot's JacksonAutoConfiguration and therefore without a shared ObjectMapper bean.
   */
  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  /** GET against the authenticated backend, decoded via a {@link ParameterizedTypeReference}. */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, ParameterizedTypeReference<T> responseType) {
    return get(uri, responseType, false);
  }

  /** GET overload that targets the anonymous public WebClient when {@code isPublic} is true. */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, ParameterizedTypeReference<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  /**
   * GET against the authenticated backend, expanding {@code uriVariables} into {@code uriTemplate}
   * so the WebClient encodes them per RFC 3986. Prefer this over hand-encoding a value into the URI
   * string: a value carrying spaces or reserved characters (e.g. a normalized blueprint product key
   * such as {@code killshot "dominion camo" rifle}) round-trips intact, whereas {@code
   * URLEncoder.encode} form-encoding (space &rarr; {@code +}) gets mangled when re-encoded across
   * the frontend&rarr;backend hop. Targets the authenticated WebClient only.
   *
   * @param uriTemplate the URI template containing {@code {name}} placeholders
   * @param responseType the decoded response type
   * @param uriVariables the values expanded into the template, encoded by the WebClient
   * @param <T> the response body type
   * @return the decoded response body
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(
      String uriTemplate, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
    return executeGet(webClient, uriTemplate, responseType, uriVariables);
  }

  /** GET overload for simple (non-generic) return types. */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, Class<T> responseType) {
    return get(uri, responseType, false);
  }

  /**
   * Class-typed GET overload that targets the anonymous public WebClient when {@code isPublic} is
   * true.
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, Class<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  /**
   * Public (anonymous) GET that expands {@code uriVariables} into {@code uriTemplate} so the
   * WebClient encodes them per RFC 3986, targeting the anonymous {@code publicWebClient}. The
   * {@code isPublic} counterpart of {@link #get(String, ParameterizedTypeReference, Object...)}:
   * use it when a free-text value (e.g. an item-search term carrying spaces or quotes) must reach a
   * {@code permitAll()} backend endpoint from an unauthenticated page, where hand-encoding the
   * value into the URI string would be re-mangled across the frontend&rarr;backend hop.
   *
   * @param uriTemplate the URI template containing {@code {name}} placeholders
   * @param responseType the decoded response type
   * @param uriVariables the values expanded into the template, encoded by the WebClient
   * @param <T> the response body type
   * @return the decoded response body
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T getPublic(
      String uriTemplate, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
    return executeGet(publicWebClient, uriTemplate, responseType, uriVariables);
  }

  /**
   * Cached GET; subsequent calls within {@link CacheConfig#STATIC_DATA_CACHE}'s TTL hit the cache.
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, ParameterizedTypeReference<T> responseType) {
    return getCached(uri, responseType, false);
  }

  /**
   * Cached GET overload that targets the anonymous public WebClient when {@code isPublic} is true.
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, ParameterizedTypeReference<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  /** Class-typed cached GET. */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, Class<T> responseType) {
    return getCached(uri, responseType, false);
  }

  /**
   * Class-typed cached GET overload that targets the anonymous public WebClient when {@code
   * isPublic} is true.
   */
  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, Class<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  /** Drops every entry in {@link CacheConfig#STATIC_DATA_CACHE}; call after admin mutations. */
  @org.springframework.cache.annotation.CacheEvict(
      cacheNames = CacheConfig.STATIC_DATA_CACHE,
      allEntries = true)
  public void clearStaticDataCache() {
    log.info("Cleared STATIC_DATA_CACHE manually");
  }

  private <T> T executeGet(
      WebClient client, String uri, ParameterizedTypeReference<T> responseType) {
    try {
      return client.get().uri(uri).retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "GET", uri);
    } catch (Exception e) {
      return handleException(e, "GET", uri);
    }
  }

  private <T> T executeGet(
      WebClient client,
      String uriTemplate,
      ParameterizedTypeReference<T> responseType,
      Object... uriVariables) {
    try {
      return client
          .get()
          .uri(uriTemplate, uriVariables)
          .retrieve()
          .bodyToMono(responseType)
          .block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "GET", uriTemplate);
    } catch (Exception e) {
      return handleException(e, "GET", uriTemplate);
    }
  }

  private <T> T executeGet(WebClient client, String uri, Class<T> responseType) {
    try {
      return client.get().uri(uri).retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "GET", uri);
    } catch (Exception e) {
      return handleException(e, "GET", uri);
    }
  }

  /**
   * POST against the authenticated backend; {@code body} may be {@code null} for empty payloads.
   */
  @CircuitBreaker(name = "backend")
  public <T, R> R post(String uri, T body, Class<R> responseType) {
    return post(uri, body, responseType, false);
  }

  /** POST overload that targets the anonymous public WebClient when {@code isPublic} is true. */
  @CircuitBreaker(name = "backend")
  public <T, R> R post(String uri, T body, Class<R> responseType, boolean isPublic) {
    return executePost(isPublic ? publicWebClient : webClient, uri, body, responseType);
  }

  private <T, R> R executePost(WebClient client, String uri, T body, Class<R> responseType) {
    try {
      WebClient.RequestBodySpec spec = client.post().uri(uri);
      WebClient.RequestHeadersSpec<?> headersSpec = (body != null) ? spec.bodyValue(body) : spec;
      return headersSpec.retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "POST", uri);
    } catch (Exception e) {
      return handleException(e, "POST", uri);
    }
  }

  /** PUT against the authenticated backend; {@code body} may be {@code null} for empty payloads. */
  @CircuitBreaker(name = "backend")
  public <T, R> R put(String uri, T body, Class<R> responseType) {
    return put(uri, body, responseType, false);
  }

  /** PUT overload that targets the anonymous public WebClient when {@code isPublic} is true. */
  @CircuitBreaker(name = "backend")
  public <T, R> R put(String uri, T body, Class<R> responseType, boolean isPublic) {
    return executePut(isPublic ? publicWebClient : webClient, uri, body, responseType);
  }

  private <T, R> R executePut(WebClient client, String uri, T body, Class<R> responseType) {
    try {
      WebClient.RequestBodySpec spec = client.put().uri(uri);
      WebClient.RequestHeadersSpec<?> headersSpec = (body != null) ? spec.bodyValue(body) : spec;
      return headersSpec.retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "PUT", uri);
    } catch (Exception e) {
      return handleException(e, "PUT", uri);
    }
  }

  /** DELETE against the authenticated backend; pass {@code Void.class} for 204 responses. */
  @CircuitBreaker(name = "backend")
  public <R> R delete(String uri, Class<R> responseType) {
    return delete(uri, responseType, false);
  }

  /** DELETE overload that targets the anonymous public WebClient when {@code isPublic} is true. */
  @CircuitBreaker(name = "backend")
  public <R> R delete(String uri, Class<R> responseType, boolean isPublic) {
    return executeDelete(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  private <R> R executeDelete(WebClient client, String uri, Class<R> responseType) {
    try {
      return client.delete().uri(uri).retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "DELETE", uri);
    } catch (Exception e) {
      return handleException(e, "DELETE", uri);
    }
  }

  /**
   * PATCH against the authenticated backend; {@code body} may be {@code null} for empty payloads.
   */
  @CircuitBreaker(name = "backend")
  public <T, R> R patch(String uri, T body, Class<R> responseType) {
    return patch(uri, body, responseType, false);
  }

  /** PATCH overload that targets the anonymous public WebClient when {@code isPublic} is true. */
  @CircuitBreaker(name = "backend")
  public <T, R> R patch(String uri, T body, Class<R> responseType, boolean isPublic) {
    return executePatch(isPublic ? publicWebClient : webClient, uri, body, responseType);
  }

  private <T, R> R executePatch(WebClient client, String uri, T body, Class<R> responseType) {
    try {
      WebClient.RequestBodySpec spec = client.patch().uri(uri);
      WebClient.RequestHeadersSpec<?> headersSpec = (body != null) ? spec.bodyValue(body) : spec;
      return headersSpec.retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "PATCH", uri);
    } catch (Exception e) {
      return handleException(e, "PATCH", uri);
    }
  }

  private <T> T handleWebClientException(WebClientResponseException e, String method, String uri) {
    BackendServiceException parsed = BackendServiceException.fromProblem(e, objectMapper);
    // Log every RFC7807 backend failure exactly once, at the boundary, so individual page
    // controllers don't have to repeat the same boilerplate. Field errors and the
    // user-facing detail are included to make a 400 VALIDATION_FAILED diagnosable from the
    // log alone (see AGENTS.md / CHANGELOG); rejected user values are never logged.
    if (parsed.getStatusCode() >= 500) {
      log.error(
          "Backend error on {} {}: status={}, code={}, correlationId={}, detail={}, fieldErrors={}",
          method,
          uri,
          parsed.getStatusCode(),
          parsed.getProblemCode(),
          parsed.getCorrelationId(),
          parsed.getProblemDetail(),
          parsed.getFieldErrors());
    } else {
      log.warn(
          "Backend client error on {} {}: status={}, code={}, correlationId={}, detail={},"
              + " fieldErrors={}",
          method,
          uri,
          parsed.getStatusCode(),
          parsed.getProblemCode(),
          parsed.getCorrelationId(),
          parsed.getProblemDetail(),
          parsed.getFieldErrors());
    }
    throw parsed;
  }

  private <T> T handleException(Exception e, String method, String uri) {
    if (ReauthenticationRequiredException.isReauthSignal(e)) {
      // The frontend OAuth2 client has no usable token for this session (access token expired and
      // the refresh token was rejected / rotated away). This is a per-session auth state, not a
      // backend health problem — log it tersely (no stack trace, DEBUG) and rethrow a typed
      // exception so GlobalExceptionHandler can bounce the user through a fresh Keycloak login
      // instead of rendering an empty page and flooding the log with stack traces (REQ-SEC-012).
      log.debug(
          "Re-authentication required on {} {} (correlationId={})",
          method,
          uri,
          MDC.get("correlationId"));
      throw new ReauthenticationRequiredException(
          "Re-authentication required for " + method + " " + uri, e);
    }
    Throwable root = unwrap(e);
    if (root instanceof CallNotPermittedException) {
      log.warn("Circuit breaker open for {} {}: {}", method, uri, root.getMessage());
      throw new BackendServiceException(
          "Backend circuit breaker open",
          e,
          503,
          BackendServiceException.CODE_SERVICE_UNAVAILABLE,
          null,
          java.util.Collections.emptyList(),
          null);
    }
    if (root instanceof BulkheadFullException) {
      log.warn("Bulkhead saturated for {} {}: {}", method, uri, root.getMessage());
      throw new BackendServiceException(
          "Backend bulkhead full",
          e,
          503,
          BackendServiceException.CODE_SERVICE_UNAVAILABLE,
          null,
          java.util.Collections.emptyList(),
          null);
    }
    if (root instanceof TimeoutException
        || root instanceof WebClientRequestException
        || root instanceof java.net.ConnectException) {
      log.warn("Backend timeout / connection failure on {} {}: {}", method, uri, root.getMessage());
      throw new BackendServiceException(
          "Backend timeout",
          e,
          504,
          BackendServiceException.CODE_BACKEND_TIMEOUT,
          null,
          java.util.Collections.emptyList(),
          null);
    }
    log.error("Unexpected backend error on {} {}: {}", method, uri, e.getMessage(), e);
    throw new BackendServiceException("Error on " + method + " data from backend", e, 500);
  }

  private static Throwable unwrap(Throwable t) {
    Throwable current = t;
    while (current != null) {
      if (current instanceof CallNotPermittedException
          || current instanceof BulkheadFullException
          || current instanceof TimeoutException
          || current instanceof WebClientRequestException
          || current instanceof java.net.ConnectException) {
        return current;
      }
      if (current.getCause() == current || current.getCause() == null) {
        return t;
      }
      current = current.getCause();
    }
    return t;
  }
}
