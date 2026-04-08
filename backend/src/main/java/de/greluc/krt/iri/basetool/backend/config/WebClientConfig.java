package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        reactor.netty.resources.ConnectionProvider provider = reactor.netty.resources.ConnectionProvider.builder("backend-pool")
                .maxConnections(50)
                .maxIdleTime(java.time.Duration.ofSeconds(20))
                .maxLifeTime(java.time.Duration.ofSeconds(60))
                .pendingAcquireTimeout(java.time.Duration.ofSeconds(5))
                .evictInBackground(java.time.Duration.ofSeconds(10))
                .build();

        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create(provider);

        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
    }
}
