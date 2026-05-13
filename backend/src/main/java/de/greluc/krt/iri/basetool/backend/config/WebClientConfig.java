package de.greluc.krt.iri.basetool.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Centrally configured {@link WebClient.Builder} bean with timeouts and a bounded connection pool.
 *
 * <p>Explicit connect/read/write timeouts (5&nbsp;s connect, 30&nbsp;s read, 30&nbsp;s write) bound
 * every layer of an outbound call so the JVM never hangs on a slow or misbehaving upstream like the
 * UEX API. The previous configuration only had a Reactor-level {@code .timeout()} on the response
 * {@code Mono}, which does NOT bound the socket-connect phase — a TCP connect to an unreachable
 * host would still wait the OS default ({@code 1+}&nbsp;min on Linux, {@code 21}&nbsp;s on
 * Windows). The connection pool is sized for the typical UEX-sync workload (50 concurrent
 * connections, 20&nbsp;s idle eviction).
 */
@Configuration
public class WebClientConfig {

  /**
   * @return a {@link WebClient.Builder} pre-configured with the Netty connector, bounded pool and
   *     timeouts; injected by services that talk to external HTTP endpoints
   */
  @Bean
  public WebClient.Builder webClientBuilder() {
    reactor.netty.resources.ConnectionProvider provider =
        reactor.netty.resources.ConnectionProvider.builder("backend-pool")
            .maxConnections(50)
            .maxIdleTime(java.time.Duration.ofSeconds(20))
            .maxLifeTime(java.time.Duration.ofSeconds(60))
            .pendingAcquireTimeout(java.time.Duration.ofSeconds(5))
            .evictInBackground(java.time.Duration.ofSeconds(10))
            .build();

    // Explicit connect/read/write timeouts so the JVM never hangs on a slow or
    // misbehaving upstream (e.g. the UEX API). The previous build only had a
    // Reactor-level .timeout() on the response Mono, which does not bound the
    // socket-connect phase.
    reactor.netty.http.client.HttpClient httpClient =
        reactor.netty.http.client.HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .responseTimeout(java.time.Duration.ofSeconds(30))
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

    return WebClient.builder()
        .clientConnector(
            new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
  }
}
