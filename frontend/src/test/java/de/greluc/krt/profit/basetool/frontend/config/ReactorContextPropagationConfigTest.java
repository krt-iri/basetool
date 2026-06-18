/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.profit.basetool.frontend.logging.ActiveSquadronContext;
import de.greluc.krt.profit.basetool.frontend.logging.CorrelationContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Verifies that {@link ReactorContextPropagationConfig} actually does what its Javadoc claims: a
 * {@link ActiveSquadronContext} value set on the calling thread is visible inside a Reactor
 * pipeline that runs on a different scheduler thread.
 *
 * <p>The regression this anchors: before this config existed, the {@code
 * ActiveSquadronRelayFilter.relayActiveSquadron()} lambda observed {@code
 * ActiveSquadronContext.get() == null} on the Reactor worker thread (because classic {@link
 * ThreadLocal} values are not copied across threads), and the outbound {@code X-Active-Org-Unit-Id}
 * header was silently dropped — leaking foreign squadrons' rows into a pinned admin's Lager view.
 * Same regression applied to {@link CorrelationContext}, breaking the correlation-id join between
 * frontend and backend log lines.
 *
 * <p>The Reactor side relies on {@link
 * reactor.core.publisher.Hooks#enableAutomaticContextPropagation()} being on. That is activated in
 * {@link ReactorContextPropagationConfig#enableContextPropagation()}; we trigger it once at {@link
 * BeforeAll} time, then run two parallel-scheduler-bound assertions — one for each registered
 * accessor.
 */
class ReactorContextPropagationConfigTest {

  @BeforeAll
  static void activateContextPropagation() {
    new ReactorContextPropagationConfig().enableContextPropagation();
  }

  @AfterEach
  void clearHolders() {
    ActiveSquadronContext.clear();
    CorrelationContext.clear();
  }

  @Test
  void activeSquadronContext_isVisibleInsideMonoOnDifferentScheduler() {
    UUID pinned = UUID.fromString("7309b226-abf0-4022-857b-f2462cc8bbb5");
    ActiveSquadronContext.set(pinned);

    AtomicReference<UUID> observedOnReactorThread = new AtomicReference<>();
    AtomicReference<String> observedThreadName = new AtomicReference<>();

    Mono.fromCallable(
            () -> {
              observedOnReactorThread.set(ActiveSquadronContext.get());
              observedThreadName.set(Thread.currentThread().getName());
              return "done";
            })
        .subscribeOn(Schedulers.parallel())
        .block();

    assertThat(observedOnReactorThread.get())
        .as("ActiveSquadronContext must be visible on the Reactor parallel-scheduler thread")
        .isEqualTo(pinned);
    assertThat(observedThreadName.get())
        .as(
            "sanity: the callable must have run on a Reactor parallel-N worker, not the JUnit"
                + " thread")
        .startsWith("parallel-");
  }

  @Test
  void correlationContext_isVisibleInsideMonoOnDifferentScheduler() {
    String correlationId = "test-correlation-" + UUID.randomUUID();
    CorrelationContext.set(correlationId);

    AtomicReference<String> observedOnReactorThread = new AtomicReference<>();

    Mono.fromCallable(
            () -> {
              observedOnReactorThread.set(CorrelationContext.get());
              return "done";
            })
        .subscribeOn(Schedulers.parallel())
        .block();

    assertThat(observedOnReactorThread.get())
        .as("CorrelationContext must be visible on the Reactor parallel-scheduler thread")
        .isEqualTo(correlationId);
  }

  @Test
  void contextsDoNotLeakAcrossSubscriptions() {
    // Set on the JUnit thread; both holders are populated.
    ActiveSquadronContext.set(UUID.fromString("7309b226-abf0-4022-857b-f2462cc8bbb5"));
    CorrelationContext.set("first-id");

    AtomicReference<UUID> firstActive = new AtomicReference<>();
    AtomicReference<String> firstCorrelation = new AtomicReference<>();
    Mono.fromCallable(
            () -> {
              firstActive.set(ActiveSquadronContext.get());
              firstCorrelation.set(CorrelationContext.get());
              return "ok";
            })
        .subscribeOn(Schedulers.parallel())
        .block();

    assertThat(firstActive.get()).isNotNull();
    assertThat(firstCorrelation.get()).isEqualTo("first-id");

    // Clear, then submit a second subscription: it must observe null on the worker, not the
    // previous subscription's snapshot. Proves the per-subscription cleanup of the SPI.
    ActiveSquadronContext.clear();
    CorrelationContext.clear();

    AtomicReference<UUID> secondActive = new AtomicReference<>();
    AtomicReference<String> secondCorrelation = new AtomicReference<>();
    Mono.fromCallable(
            () -> {
              secondActive.set(ActiveSquadronContext.get());
              secondCorrelation.set(CorrelationContext.get());
              return "ok";
            })
        .subscribeOn(Schedulers.parallel())
        .block();

    assertThat(secondActive.get())
        .as("second subscription must not see the first subscription's pin")
        .isNull();
    assertThat(secondCorrelation.get())
        .as("second subscription must not see the first subscription's correlation id")
        .isNull();
  }
}
