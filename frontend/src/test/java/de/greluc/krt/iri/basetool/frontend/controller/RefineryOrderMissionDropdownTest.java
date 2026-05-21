package de.greluc.krt.iri.basetool.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifiziert die Filter- und Sortierregeln des Einsatz-Dropdowns auf den Raffinerieauftrag-Seiten:
 * nur Einsaetze der letzten drei Monate (sowie zukuenftig geplante), absteigend nach {@code
 * plannedStartTime}; aktuell verknuepfte Einsaetze bleiben auch ausserhalb des Fensters sichtbar,
 * damit das Drop-down bei bestehenden Auftraegen die Verknuepfung nicht stillschweigend entfernt.
 */
class RefineryOrderMissionDropdownTest {

  @Test
  void shouldDropMissionsBeforeCutoffAndSortNewestFirst() {
    // Given
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    MissionListDto fourMonthsAgo = mission(Instant.parse("2026-01-15T10:00:00Z"));
    MissionListDto oneMonthAgo = mission(Instant.parse("2026-04-15T10:00:00Z"));
    MissionListDto twoWeeksAgo = mission(Instant.parse("2026-05-05T10:00:00Z"));

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(fourMonthsAgo, oneMonthAgo, twoWeeksAgo), cutoff, null);

    // Then: only the two within-window missions, newest first
    assertEquals(2, result.size());
    assertEquals(twoWeeksAgo.id(), result.get(0).id());
    assertEquals(oneMonthAgo.id(), result.get(1).id());
  }

  @Test
  void shouldIncludeFutureScheduledMissions() {
    // Given: a future-scheduled mission must appear above current ones (newer == higher)
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    MissionListDto future = mission(Instant.parse("2026-07-01T10:00:00Z"));
    MissionListDto recent = mission(Instant.parse("2026-05-01T10:00:00Z"));

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(recent, future), cutoff, null);

    // Then
    assertEquals(2, result.size());
    assertEquals(future.id(), result.get(0).id());
    assertEquals(recent.id(), result.get(1).id());
  }

  @Test
  void shouldDropMissionsWithoutPlannedStartTime() {
    // Given
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    MissionListDto inWindow = mission(Instant.parse("2026-04-15T10:00:00Z"));
    MissionListDto noDate = mission(null);

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(inWindow, noDate), cutoff, null);

    // Then
    assertEquals(1, result.size());
    assertEquals(inWindow.id(), result.get(0).id());
  }

  @Test
  void shouldTreatCutoffAsInclusive() {
    // Given: a mission exactly on the cutoff stays in the result
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    MissionListDto onCutoff = mission(cutoff);

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(onCutoff), cutoff, null);

    // Then
    assertEquals(1, result.size());
    assertEquals(onCutoff.id(), result.get(0).id());
  }

  @Test
  void shouldPreserveSelectedMissionEvenIfOutsideWindow() {
    // Given: an existing refinery order links to a mission older than three months
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    UUID preserveId = UUID.randomUUID();
    MissionListDto stale = missionWithId(preserveId, Instant.parse("2025-09-01T10:00:00Z"));
    MissionListDto recent = mission(Instant.parse("2026-04-15T10:00:00Z"));

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(stale, recent), cutoff, preserveId);

    // Then: the recent mission leads, the preserved mission tags along so the form still has it
    assertEquals(2, result.size());
    assertEquals(recent.id(), result.get(0).id());
    assertTrue(result.stream().anyMatch(m -> preserveId.equals(m.id())));
  }

  @Test
  void shouldNotDuplicatePreservedMissionAlreadyInWindow() {
    // Given: the preserved id is already inside the three-month window
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    UUID preserveId = UUID.randomUUID();
    MissionListDto inWindow = missionWithId(preserveId, Instant.parse("2026-04-15T10:00:00Z"));

    // When
    List<MissionListDto> result =
        RefineryOrderPageController.filterAndSortMissionsForDropdown(
            List.of(inWindow), cutoff, preserveId);

    // Then: a single entry, no double-append
    assertEquals(1, result.size());
  }

  @Test
  void shouldReturnEmptyListForNullOrEmptyInput() {
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");

    assertTrue(
        RefineryOrderPageController.filterAndSortMissionsForDropdown(null, cutoff, null).isEmpty());
    assertTrue(
        RefineryOrderPageController.filterAndSortMissionsForDropdown(List.of(), cutoff, null)
            .isEmpty());
  }

  private MissionListDto mission(Instant plannedStartTime) {
    return missionWithId(UUID.randomUUID(), plannedStartTime);
  }

  private MissionListDto missionWithId(UUID id, Instant plannedStartTime) {
    return new MissionListDto(
        id,
        "m",
        null,
        null,
        "OPEN",
        null,
        plannedStartTime,
        null,
        null,
        null,
        false,
        null,
        null,
        1L);
  }
}
