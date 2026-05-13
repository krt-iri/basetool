package de.greluc.krt.iri.basetool.backend.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static de.greluc.krt.iri.basetool.backend.model.OperationStatus.ACTIVE;
import static de.greluc.krt.iri.basetool.backend.model.OperationStatus.CANCELED;
import static de.greluc.krt.iri.basetool.backend.model.OperationStatus.COMPLETED;
import static de.greluc.krt.iri.basetool.backend.model.OperationStatus.PLANNED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the lifecycle state machine for an operation. Service callers other
 * than ROLE_ADMIN are gated on these rules; loosening one transition here
 * silently loosens the API contract too.
 */
class OperationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            // Same-status updates are always allowed (rename / re-description path).
            "PLANNED, PLANNED",
            "ACTIVE, ACTIVE",
            "COMPLETED, COMPLETED",
            "CANCELED, CANCELED",
            // Forward progress through the happy path.
            "PLANNED, ACTIVE",
            "ACTIVE, COMPLETED",
            // Cancellation is allowed from any non-terminal state.
            "PLANNED, CANCELED",
            "ACTIVE, CANCELED"
    })
    void allowedTransitions(OperationStatus from, OperationStatus to) {
        assertTrue(from.canTransitionTo(to),
                "expected " + from + " -> " + to + " to be allowed");
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            // PLANNED cannot skip the ACTIVE phase.
            "PLANNED, COMPLETED",
            // ACTIVE cannot un-start.
            "ACTIVE, PLANNED",
            // COMPLETED is terminal.
            "COMPLETED, PLANNED",
            "COMPLETED, ACTIVE",
            "COMPLETED, CANCELED",
            // CANCELED is terminal.
            "CANCELED, PLANNED",
            "CANCELED, ACTIVE",
            "CANCELED, COMPLETED"
    })
    void rejectedTransitions(OperationStatus from, OperationStatus to) {
        assertFalse(from.canTransitionTo(to),
                "expected " + from + " -> " + to + " to be rejected");
    }

    @Test
    void everyStatusCanTransitionToItself() {
        // Even terminal statuses must permit a same-status update so the caller
        // can rename / re-describe the entity without tripping the gate.
        for (OperationStatus s : OperationStatus.values()) {
            assertTrue(s.canTransitionTo(s));
        }
    }
}
