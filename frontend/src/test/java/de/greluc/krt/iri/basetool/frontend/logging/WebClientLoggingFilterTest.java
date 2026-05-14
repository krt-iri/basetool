package de.greluc.krt.iri.basetool.frontend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.greluc.krt.iri.basetool.frontend.config.LoggingProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientLoggingFilterTest {

  private MockWebServer server;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;
  private LoggingProperties props;
  private WebClientLoggingFilter filter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    props = new LoggingProperties();
    filter = new WebClientLoggingFilter(props);
    logger = (Logger) LoggerFactory.getLogger(WebClientLoggingFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() throws Exception {
    logger.detachAppender(appender);
    server.shutdown();
    CorrelationContext.clear();
  }

  @Test
  void propagatesCorrelationIdHeaderWhenPresent() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
    CorrelationContext.set("cid-xyz");
    WebClient wc =
        WebClient.builder()
            .baseUrl(server.url("/").toString())
            .filter(filter.correlationIdPropagation())
            .build();

    wc.get().uri("/x").retrieve().toBodilessEntity().block();

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader("X-Correlation-Id")).isEqualTo("cid-xyz");
  }

  @Test
  void doesNotAddCorrelationIdHeaderWhenAbsent() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
    WebClient wc =
        WebClient.builder()
            .baseUrl(server.url("/").toString())
            .filter(filter.correlationIdPropagation())
            .build();

    wc.get().uri("/x").retrieve().toBodilessEntity().block();

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getHeader("X-Correlation-Id")).isNull();
  }

  @Test
  void logsInfoLineOnFastSuccess() {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
    WebClient wc =
        WebClient.builder()
            .baseUrl(server.url("/").toString())
            .filter(filter.callLogging())
            .build();

    wc.get().uri("/api/v1/ok").retrieve().toBodilessEntity().block();

    assertThat(appender.list)
        .anyMatch(
            e ->
                e.getLevel() == Level.INFO
                    && e.getFormattedMessage().contains("GET")
                    && e.getFormattedMessage().contains("/api/v1/ok")
                    && e.getFormattedMessage().contains("-> 200"));
  }

  @Test
  void escalatesToWarnOnServerError() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody(""));
    WebClient wc =
        WebClient.builder()
            .baseUrl(server.url("/").toString())
            .filter(filter.callLogging())
            .build();

    try {
      wc.get().uri("/api/v1/boom").retrieve().toBodilessEntity().block();
    } catch (Exception ignored) {
      // expected – 500 is mapped to WebClientResponseException
    }

    assertThat(appender.list)
        .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("-> 500"));
  }
}
