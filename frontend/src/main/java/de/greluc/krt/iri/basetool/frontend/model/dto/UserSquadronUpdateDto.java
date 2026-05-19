package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of {@code UserController.UpdateUserSquadronRequest}. Carries the squadron id the
 * admin selected (or {@code null} when the admin cleared the assignment) plus the optimistic-lock
 * version of the user row the admin last fetched.
 */
public record UserSquadronUpdateDto(@Nullable UUID squadronId, Long version) {}
