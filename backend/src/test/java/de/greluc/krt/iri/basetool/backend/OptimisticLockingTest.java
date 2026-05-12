package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.HangarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link HangarService#updateShip} under real concurrent contention
 * to verify that the {@code @Version}-based optimistic-locking guard holds end
 * to end (service-layer pre-check + JPA UPDATE-WHERE-VERSION fallback).
 *
 * <p>The previous incarnation of this test fetched the first row from
 * {@code shipRepository.findAll()} and updated it, then asserted only that the
 * result was non-null — but the test database (Postgres Testcontainer with
 * Flyway-managed schema only) contains no ships, so the {@code findAll()}
 * stream resolved to {@code null} and the entire body became dead code: the
 * test always passed without ever invoking the service.
 *
 * <p>This rewrite seeds a real Ship + owner + type, then launches several
 * worker threads that each request the same update with the same stale
 * version. A {@link CountDownLatch} barrier holds them until every thread has
 * built its DTO so the simultaneous-update race is genuine. Exactly one thread
 * must win and every other one must observe an
 * {@link ObjectOptimisticLockingFailureException} — surfaced either by the
 * explicit version check at the top of {@code updateShip} or by Hibernate's
 * UPDATE-rows-affected-zero fallback on commit, depending on which thread got
 * to the {@code findById} first.
 *
 * <p>Intentionally <em>not</em> {@code @Transactional}: a test-managed
 * transaction would be invisible to the worker threads (each running in its
 * own session), and the seed Ship would never be readable. Per-test
 * row-leakage into the Testcontainer Postgres is acceptable because the
 * container lives only for the duration of the Gradle run.
 */
@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingTest {

    private static final int THREADS = 5;
    private static final int START_TIMEOUT_SECONDS = 5;
    private static final int FINISH_TIMEOUT_SECONDS = 30;

    @Autowired
    private HangarService hangarService;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private ShipTypeRepository shipTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID ownerId;
    private UUID shipTypeId;
    private UUID shipId;
    private Long initialVersion;

    @AfterEach
    void cleanupSeedRows() {
        // Without an outer @Transactional we keep the rows alive across the test;
        // siblings like ShipTypeTest assume `shipTypeRepository.findAll().get(0)`
        // returns *their* row, so we explicitly remove our seed entities in
        // reverse FK order (Ship -> ShipType + User) to keep them isolated.
        if (shipId != null) {
            shipRepository.deleteById(shipId);
        }
        if (shipTypeId != null) {
            shipTypeRepository.deleteById(shipTypeId);
        }
        if (ownerId != null) {
            userRepository.deleteById(ownerId);
        }
    }

    @BeforeEach
    void seedShip() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("oltest-" + owner.getId());
        userRepository.save(owner);
        ownerId = owner.getId();

        ShipType type = new ShipType();
        type.setName("OL-Test Type " + UUID.randomUUID());
        shipTypeId = shipTypeRepository.save(type).getId();

        Ship ship = new Ship();
        ship.setName("Concurrent Ship " + UUID.randomUUID());
        ship.setInsurance("LTI");
        ship.setShipType(type);
        ship.setOwner(owner);
        ship.setFitted(true);
        Ship saved = shipRepository.save(ship);
        shipId = saved.getId();
        initialVersion = saved.getVersion();
        assertNotNull(initialVersion, "freshly persisted ship must carry a @Version value");
    }

    @Test
    void hangarServiceUpdateShip_underRealConcurrentContention_exactlyOneThreadWins() throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger otherErrorCount = new AtomicInteger();
        List<Throwable> unexpectedErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        ShipRequestDto dto = new ShipRequestDto(
                                "Updated by thread " + idx,
                                shipTypeId,
                                "LTI",
                                null,
                                true,
                                initialVersion);
                        ready.countDown();
                        if (!go.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            otherErrorCount.incrementAndGet();
                            return;
                        }
                        try {
                            hangarService.updateShip(ownerId, shipId, dto);
                            successCount.incrementAndGet();
                        } catch (ObjectOptimisticLockingFailureException expected) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        otherErrorCount.incrementAndGet();
                        unexpectedErrors.add(t);
                    }
                }));
            }

            assertTrue(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "all worker threads should have built their DTO within "
                            + START_TIMEOUT_SECONDS + "s");
            go.countDown();

            for (Future<?> f : futures) {
                f.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(0, otherErrorCount.get(),
                () -> "no thread should have thrown an unexpected exception, got: "
                        + unexpectedErrors);
        assertEquals(1, successCount.get(),
                "exactly one updateShip call should succeed under contention");
        assertEquals(THREADS - 1, conflictCount.get(),
                "every other thread should see ObjectOptimisticLockingFailureException");

        Ship persisted = shipRepository.findById(shipId).orElseThrow();
        assertTrue(persisted.getName().startsWith("Updated by thread "),
                "winning thread's update must be the one that landed in the DB, got: "
                        + persisted.getName());
        assertEquals(initialVersion + 1, persisted.getVersion(),
                "version must be incremented exactly once across the whole race");
    }
}
