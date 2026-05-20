package de.greluc.krt.iri.basetool.frontend.config;

import de.greluc.krt.iri.basetool.frontend.logging.ActiveSquadronRelayFilter;
import de.greluc.krt.iri.basetool.frontend.logging.WebClientLoggingFilter;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Spring configuration for Web Client. */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

  private final AppBackendProperties backendProperties;
  private final AppHttpProperties httpProperties;
  private final WebClientLoggingFilter webClientLoggingFilter;
  private final ActiveSquadronRelayFilter activeSquadronRelayFilter;
  private final org.springframework.core.env.Environment environment;

  /**
   * Builds the Netty SSL context for the backend WebClient. {@link InsecureTrustManagerFactory} (=
   * accept any certificate) is only attached when the active Spring profile is {@code dev} or
   * {@code test} — the bundled bootstrap {@code keystore.p12} cert is self-signed and the test
   * docker stack uses an ephemeral cert. In every other profile (including {@code prod}) the
   * default JVM trust store is used, so a man-in-the-middle on the network between frontend and
   * backend cannot intercept the bearer-token-relay traffic (audit finding M-13).
   */
  private ReactorClientHttpConnector connector() {
    try {
      SslContextBuilder builder = SslContextBuilder.forClient();
      java.util.List<String> profiles = java.util.Arrays.asList(environment.getActiveProfiles());
      if (profiles.contains("dev") || profiles.contains("test")) {
        builder = builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      }
      SslContext sslContext = builder.build();

      // Pool sized at 100 connections: a single mission-detail render now fans out to four
      // parallel backend calls via `ParallelPageLoader`, so ~25 concurrent users can exhaust a
      // 50-slot pool. 100 gives comfortable headroom; the `pendingAcquireTimeout` is raised in
      // step from 5 s to 10 s so a transient backend slowdown queues rather than failing fast,
      // which the page render cannot recover from gracefully. Idle/life timeouts unchanged.
      reactor.netty.resources.ConnectionProvider provider =
          reactor.netty.resources.ConnectionProvider.builder("frontend-pool")
              .maxConnections(100)
              .maxIdleTime(java.time.Duration.ofSeconds(20))
              .maxLifeTime(java.time.Duration.ofSeconds(60))
              .pendingAcquireTimeout(java.time.Duration.ofSeconds(10))
              .evictInBackground(java.time.Duration.ofSeconds(10))
              .build();

      HttpClient httpClient =
          HttpClient.create(provider)
              .secure(t -> t.sslContext(sslContext))
              .option(
                  ChannelOption.CONNECT_TIMEOUT_MILLIS,
                  Math.toIntExact(httpProperties.getConnectTimeout().toMillis()))
              .responseTimeout(httpProperties.getResponseTimeout())
              .doOnConnected(
                  conn ->
                      conn.addHandlerLast(
                              new ReadTimeoutHandler(
                                  httpProperties.getReadTimeout().toMillis(),
                                  TimeUnit.MILLISECONDS))
                          .addHandlerLast(
                              new WriteTimeoutHandler(
                                  httpProperties.getWriteTimeout().toMillis(),
                                  TimeUnit.MILLISECONDS)));
      return new ReactorClientHttpConnector(httpClient);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize SSL context", e);
    }
  }

  private ExchangeFilterFunction resilienceFilter(
      String instanceName,
      CircuitBreakerRegistry cbRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry,
      BulkheadRegistry bulkheadRegistry) {
    CircuitBreaker cb = cbRegistry.circuitBreaker(instanceName);
    Retry retry = retryRegistry.retry(instanceName);
    TimeLimiter tl = timeLimiterRegistry.timeLimiter(instanceName);
    Bulkhead bh = bulkheadRegistry.bulkhead(instanceName);

    return (request, next) ->
        next.exchange(request)
            // Map HTTP 4xx/5xx to exceptions BEFORE applying resilience operators,
            // so Retry/CircuitBreaker can react properly
            .flatMap(
                resp -> {
                  if (resp.statusCode().is4xxClientError()
                      || resp.statusCode().is5xxServerError()) {
                    return resp.createException().flatMap(Mono::error);
                  }
                  return reactor.core.publisher.Mono.just(resp);
                })
            // Apply operators (order: bulkhead -> timeLimiter -> retry -> circuitBreaker)
            // Retry before CB so all retry attempts are executed against backend;
            // CB will evaluate across top-level calls.
            .transformDeferred(BulkheadOperator.of(bh))
            .transformDeferred(TimeLimiterOperator.of(tl))
            .transformDeferred(
                mono -> {
                  String method = request.method().name();
                  if ("GET".equals(method)
                      || "HEAD".equals(method)
                      || "OPTIONS".equals(method)
                      || "TRACE".equals(method)) {
                    return mono.transformDeferred(RetryOperator.of(retry));
                  }
                  return mono;
                })
            .transformDeferred(CircuitBreakerOperator.of(cb));
  }

  /**
   * OAuth2 authorised-client manager providing {@code authorization_code} and {@code refresh_token}
   * flows for the authenticated backend WebClient.
   */
  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build();

    DefaultOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  /**
   * Authenticated WebClient against the backend: 16 MB max in-memory codec, Resilience4j chain
   * (timeout, retry, circuit breaker, bulkhead), correlation-id propagation, OAuth2 bearer relay,
   * defaults to {@code Accept: application/json}.
   */
  @Bean
  public WebClient webClient(
      OAuth2AuthorizedClientManager authorizedClientManager,
      CircuitBreakerRegistry cbRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry,
      BulkheadRegistry bulkheadRegistry) {
    ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Client.setDefaultOAuth2AuthorizedClient(true);
    oauth2Client.setDefaultClientRegistrationId("keycloak");

    ExchangeStrategies strategies =
        ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    return WebClient.builder()
        .exchangeStrategies(strategies)
        .clientConnector(connector())
        .apply(oauth2Client.oauth2Configuration())
        .filter(webClientLoggingFilter.correlationIdPropagation())
        .filter(activeSquadronRelayFilter.relayActiveSquadron())
        .filter(webClientLoggingFilter.callLogging())
        .filter(
            resilienceFilter(
                "backendApi", cbRegistry, retryRegistry, timeLimiterRegistry, bulkheadRegistry))
        .defaultHeaders(headers -> headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON)))
        .baseUrl(backendProperties.getBackendUrl())
        .build();
  }

  /**
   * Anonymous WebClient against the backend's public endpoints. Same resilience and logging chain
   * as {@link #webClient} but without OAuth2 bearer relay.
   */
  @Bean
  public WebClient publicWebClient(
      CircuitBreakerRegistry cbRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry,
      BulkheadRegistry bulkheadRegistry) {
    ExchangeStrategies strategies =
        ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    return WebClient.builder()
        .exchangeStrategies(strategies)
        .clientConnector(connector())
        .filter(webClientLoggingFilter.correlationIdPropagation())
        .filter(webClientLoggingFilter.callLogging())
        .filter(
            resilienceFilter(
                "backendApi", cbRegistry, retryRegistry, timeLimiterRegistry, bulkheadRegistry))
        .defaultHeaders(headers -> headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON)))
        .baseUrl(backendProperties.getBackendUrl())
        .build();
  }
}
