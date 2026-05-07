package de.greluc.krt.iri.basetool.frontend.logging;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Resilience4j event publishers (circuit breaker, retry, bulkhead, time limiter)
 * and mirrors every event onto the application log. This is crucial for production debugging
 * because state transitions and rejections otherwise happen silently: a circuit breaker opens,
 * all backend calls start failing with {@code SERVICE_UNAVAILABLE}, but without this logger
 * there is no direct signal in the log file explaining <em>why</em>.
 *
 * <p>State transitions are logged at WARN, individual retry attempts at INFO (DEBUG when they
 * eventually succeed), bulkhead rejections at WARN, and time-limiter timeouts at WARN. The
 * information contained is purely technical (instance name, old/new state, attempt count) and
 * does not leak request-scoped data.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilienceEventLogger {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    @PostConstruct
    void subscribe() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.getEventPublisher()
                .onStateTransition(e -> log.warn("CircuitBreaker[{}] state transition {} -> {}",
                        cb.getName(), e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()))
                .onCallNotPermitted(e -> log.warn("CircuitBreaker[{}] call not permitted", cb.getName()))
                .onError(e -> log.info("CircuitBreaker[{}] call failed: {}", cb.getName(),
                        e.getThrowable().getClass().getSimpleName())));

        retryRegistry.getAllRetries().forEach(retry -> retry.getEventPublisher()
                .onRetry(e -> log.info("Retry[{}] attempt {} after {}: {}", retry.getName(),
                        e.getNumberOfRetryAttempts(), e.getWaitInterval(),
                        e.getLastThrowable() != null ? e.getLastThrowable().getClass().getSimpleName() : "n/a"))
                .onError(e -> log.warn("Retry[{}] exhausted after {} attempts", retry.getName(),
                        e.getNumberOfRetryAttempts())));

        bulkheadRegistry.getAllBulkheads().forEach(bh -> bh.getEventPublisher()
                .onCallRejected(e -> log.warn("Bulkhead[{}] call rejected", bh.getName())));

        timeLimiterRegistry.getAllTimeLimiters().forEach(tl -> tl.getEventPublisher()
                .onTimeout(e -> log.warn("TimeLimiter[{}] call timed out", tl.getName())));
    }
}
