package de.greluc.krt.iri.basetool.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

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
