package de.greluc.krt.iri.basetool.frontend.model.dto;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;

public record OperationPayoutDto(
    String participantId,
    String participantName,
    double participationPercentage,
    PayoutPreference payoutPreference) {}
