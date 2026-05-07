package de.greluc.krt.iri.basetool.frontend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ResilienceEventLoggerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ResilienceEventLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void logsCircuitBreakerStateTransitionAsWarn() {
        CircuitBreakerRegistry cbReg = CircuitBreakerRegistry.ofDefaults();
        // Force a circuit breaker to exist so the logger subscribes to it.
        CircuitBreaker cb = cbReg.circuitBreaker("test-instance");
        RetryRegistry retryReg = RetryRegistry.ofDefaults();
        BulkheadRegistry bhReg = BulkheadRegistry.ofDefaults();
        TimeLimiterRegistry tlReg = TimeLimiterRegistry.ofDefaults();

        ResilienceEventLogger rel = new ResilienceEventLogger(cbReg, retryReg, bhReg, tlReg);
        rel.subscribe();

        cb.transitionToOpenState();

        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("CircuitBreaker[test-instance]")
                        && e.getFormattedMessage().contains("state transition"));
    }
}
