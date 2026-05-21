package de.greluc.krt.iri.basetool.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit tests for {@link AsyncConfig}. The bean produced by {@link AsyncConfig#uexExecutor()} is the
 * sole bound on UEX-async thread growth; the assertions pin the sizing contract so a refactor that
 * "simplifies" the pool back to defaults breaks the build before it ships.
 */
class AsyncConfigTest {

  private AsyncConfig config;
  private ThreadPoolTaskExecutor created;

  @BeforeEach
  void setUp() {
    config = new AsyncConfig();
  }

  @AfterEach
  void tearDown() {
    if (created != null) {
      created.shutdown();
    }
  }

  @Test
  void uexExecutor_isThreadPoolTaskExecutorWithPinnedSizing() {
    Executor executor = config.uexExecutor();
    assertNotNull(executor);
    created = assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

    assertEquals(2, created.getCorePoolSize(), "core pool size");
    assertEquals(4, created.getMaxPoolSize(), "max pool size");
    assertEquals(100, created.getQueueCapacity(), "queue capacity");
    assertEquals("uex-async-", created.getThreadNamePrefix(), "thread name prefix");
  }

  @Test
  void uexExecutor_usesAbortPolicyToFailLoudOnSaturation() {
    Executor executor = config.uexExecutor();
    created = (ThreadPoolTaskExecutor) executor;

    ThreadPoolExecutor underlying = created.getThreadPoolExecutor();
    assertInstanceOf(
        ThreadPoolExecutor.AbortPolicy.class,
        underlying.getRejectedExecutionHandler(),
        "saturation must surface as RejectedExecutionException, not silent backpressure");
  }

  @Test
  void uexExecutorBeanName_isStableConstant() {
    // The constant is referenced from @Async(AsyncConfig.UEX_EXECUTOR) on UexScheduler;
    // changing it silently would break that wiring at runtime, not at compile time.
    assertEquals("uexExecutor", AsyncConfig.UEX_EXECUTOR);
  }
}
