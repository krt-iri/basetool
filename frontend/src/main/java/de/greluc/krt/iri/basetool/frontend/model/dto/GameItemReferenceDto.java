package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code GameItemReferenceDto}: a slim reference to an orderable
 * finished item (id, name, and the {@code GameItemKind} name as a string).
 *
 * @param id the game item's id
 * @param name the item's display name
 * @param kind the item kind name (e.g. {@code WEAPON})
 */
public record GameItemReferenceDto(UUID id, String name, String kind) {}
