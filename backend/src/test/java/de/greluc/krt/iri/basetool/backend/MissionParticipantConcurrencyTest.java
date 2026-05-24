package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Pins the multi-user concurrency guarantee for the participant signup flow: {@code N} users may
 * sign up to the same mission in parallel without any thread seeing an {@link
 * ObjectOptimisticLockingFailureException}, and {@link Mission#getVersion()} stays unchanged across
 * the whole batch.
 *
 * <p>The guarantee is the load-bearing reason why {@link
 * de.greluc.krt.iri.basetool.backend.model.Mission#getParticipants()} carries
 * {@code @OptimisticLock(excluded = true)} and why {@link MissionService#addParticipant}
 * deliberately does not call {@code missionRepository.save(mission)} (see the inline comment on the
 * service method). A future change that removes either of those by accident would re-introduce 409s
 * on every concurrent "Anmelden" click — this test catches the regression at build time.
 *
 * <p>Sibling guard {@code ArchitectureTest#missionParticipantsCollectionMustExcludeOptimisticLock}
 * and {@code ArchitectureTest#missionServiceAddParticipantMustNotSaveMission} encode the same
 * invariants as static bytecode checks; this {@code @SpringBootTest} additionally proves the
 * dynamic behaviour against a real Hibernate session.
 *
 * <p>Modelled after {@link ConcurrencyTest}: deliberately NOT {@code @Transactional} so each worker
 * thread runs in its own session, and the seed rows are cleaned up via {@code @AfterEach} so
 * adjacent tests inherit a clean baseline.
 */
@SpringBootTest
@ActiveProfiles("test")
class MissionParticipantConcurrencyTest {

  private static final int THREADS = 5;
  private static final int START_TIMEOUT_SECONDS = 5;
  private static final int FINISH_TIMEOUT_SECONDS = 30;

  @Autowired private SquadronRepository squadronRepository;
  @Autowired private MissionRepository missionRepository;
  @Autowired private MissionParticipantRepository missionParticipantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MissionService missionService;

  @MockitoBean private JwtDecoder jwtDecoder;

  private UUID seedMissionId;
  private final List<UUID> seedUserIds = new ArrayList<>();

  /**
   * Removes the seed mission, its participants and the seed users so the next test starts from a
   * clean state. Without an outer {@code @Transactional} the rows persist across tests.
   */
  @AfterEach
  void cleanupSeedRows() {
    if (seedMissionId != null) {
      missionParticipantRepository.deleteAll(
          missionParticipantRepository.findAll().stream()
              .filter(p -> seedMissionId.equals(p.getMission().getId()))
              .toList());
      missionRepository.deleteById(seedMissionId);
      seedMissionId = null;
    }
    for (UUID userId : seedUserIds) {
      userRepository.deleteById(userId);
    }
    seedUserIds.clear();
  }

  /**
   * {@value THREADS} threads add {@value THREADS} distinct users to the same mission in lockstep.
   * The {@code go} latch synchronises every worker so the {@code INSERT mission_participant}
   * statements race against the database, mirroring the realistic case of multiple users hitting
   * the "Anmelden" button within the same millisecond.
   *
   * <p>The expected outcome is {@value THREADS} successes and zero conflicts because the parent
   * {@code mission} row is never updated (only the inverse-side {@code mission_participant} child
   * rows are inserted), so Hibernate has no version column to race on. Any thread throwing {@link
   * ObjectOptimisticLockingFailureException} indicates a regression in the
   * {@code @OptimisticLock(excluded = true)} annotation on {@link
   * de.greluc.krt.iri.basetool.backend.model.Mission#getParticipants()} or in the {@link
   * MissionService#addParticipant} body (e.g. a stray {@code missionRepository.save(mission)} call
   * that dirties the parent row).
   */
  @Test
  void addParticipant_underRealConcurrentContention_allThreadsSucceed() throws Exception {
    Squadron iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();
    Mission seed = new Mission();
    seed.setOwningSquadron(iridium);
    seed.setName("Concurrency Signup Mission " + UUID.randomUUID());
    seed.setStatus("PLANNED");
    seedMissionId = missionRepository.save(seed).getId();
    final UUID missionId = seedMissionId;
    final Long versionBeforeAdds = missionRepository.findById(missionId).orElseThrow().getVersion();

    final List<UUID> userIds = new ArrayList<>(THREADS);
    for (int i = 0; i < THREADS; i++) {
      User user = new User();
      user.setId(UUID.randomUUID());
      user.setUsername("concurrent-signup-user-" + i + "-" + UUID.randomUUID());
      User saved = userRepository.save(user);
      userIds.add(saved.getId());
      seedUserIds.add(saved.getId());
    }

    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger conflictCount = new AtomicInteger();
    AtomicInteger otherErrorCount = new AtomicInteger();
    List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    List<Future<?>> futures = new ArrayList<>(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        final UUID userId = userIds.get(i);
        futures.add(
            pool.submit(
                () -> {
                  try {
                    ready.countDown();
                    if (!go.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                      otherErrorCount.incrementAndGet();
                      return;
                    }
                    missionService.addParticipant(missionId, userId);
                    successCount.incrementAndGet();
                  } catch (ObjectOptimisticLockingFailureException unexpected) {
                    conflictCount.incrementAndGet();
                    unexpectedErrors.add(unexpected);
                  } catch (Throwable t) {
                    otherErrorCount.incrementAndGet();
                    unexpectedErrors.add(t);
                  }
                }));
      }

      assertTrue(
          ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "all worker threads should have entered the race within " + START_TIMEOUT_SECONDS + "s");
      go.countDown();

      for (Future<?> f : futures) {
        f.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    assertEquals(
        0,
        conflictCount.get(),
        () ->
            "no thread should see ObjectOptimisticLockingFailureException — Mission.participants is"
                + " annotated @OptimisticLock(excluded = true) and addParticipant must not bump the"
                + " parent version. Got conflicts: "
                + unexpectedErrors);
    assertEquals(
        0,
        otherErrorCount.get(),
        () -> "no thread should throw any other exception, got: " + unexpectedErrors);
    assertEquals(THREADS, successCount.get(), "every concurrent signup must succeed");

    Mission persisted = missionRepository.findById(missionId).orElseThrow();
    assertEquals(
        THREADS,
        persisted.getParticipants().size(),
        "every concurrent signup must result in a participant row");
    assertNotNull(versionBeforeAdds, "seed mission must have a version stamped after save");
    assertEquals(
        versionBeforeAdds,
        persisted.getVersion(),
        "Mission.version must not bump on participant adds (Option A: @OptimisticLock excluded on"
            + " the participants collection) — concurrent edits on other mission sections must stay"
            + " independent of signup activity");
  }
}
