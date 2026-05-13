package de.greluc.krt.iri.basetool.frontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.frontend.config.CacheConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, ParameterizedTypeReference<T> responseType) {
    return get(uri, responseType, false);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, ParameterizedTypeReference<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, Class<T> responseType) {
    return get(uri, responseType, false);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  public <T> T get(String uri, Class<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, ParameterizedTypeReference<T> responseType) {
    return getCached(uri, responseType, false);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, ParameterizedTypeReference<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, Class<T> responseType) {
    return getCached(uri, responseType, false);
  }

  @Retry(name = "backend")
  @CircuitBreaker(name = "backend")
  @Cacheable(cacheNames = CacheConfig.STATIC_DATA_CACHE, key = "#uri")
  public <T> T getCached(String uri, Class<T> responseType, boolean isPublic) {
    return executeGet(isPublic ? publicWebClient : webClient, uri, responseType);
  }

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

  private <T> T executeGet(WebClient client, String uri, Class<T> responseType) {
    try {
      return client.get().uri(uri).retrieve().bodyToMono(responseType).block();
    } catch (WebClientResponseException e) {
      return handleWebClientException(e, "GET", uri);
    } catch (Exception e) {
      return handleException(e, "GET", uri);
    }
  }

  @CircuitBreaker(name = "backend")
  public <T, R> R post(String uri, T body, Class<R> responseType) {
    return post(uri, body, responseType, false);
  }

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

  @CircuitBreaker(name = "backend")
  public <T, R> R put(String uri, T body, Class<R> responseType) {
    return put(uri, body, responseType, false);
  }

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

  @CircuitBreaker(name = "backend")
  public <R> R delete(String uri, Class<R> responseType) {
    return delete(uri, responseType, false);
  }

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

  @CircuitBreaker(name = "backend")
  public <T, R> R patch(String uri, T body, Class<R> responseType) {
    return patch(uri, body, responseType, false);
  }

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
          "Backend client error on {} {}: status={}, code={}, correlationId={}, detail={}, fieldErrors={}",
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
