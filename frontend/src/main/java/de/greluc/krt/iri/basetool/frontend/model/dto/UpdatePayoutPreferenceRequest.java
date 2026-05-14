package de.greluc.krt.iri.basetool.frontend.model.dto;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;

/** Inbound request payload for the Update Payout Preference operation. */
public record UpdatePayoutPreferenceRequest(PayoutPreference preference) {}
