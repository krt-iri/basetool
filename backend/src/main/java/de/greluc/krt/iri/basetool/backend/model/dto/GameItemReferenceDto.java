package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Slim reference projection of a {@code GameItem} for item-order payloads and the orderable-item
 * picker. Carries only what the order UI needs to identify and label a finished item — its id,
 * name, and {@code kind} (the {@code GameItemKind} name, exposed as a string for API stability) —
 * without dragging the full catalogue entity across the boundary.
 *
 * @param id the game item's primary key
 * @param name the item's display name
 * @param kind the {@code GameItemKind} name (e.g. {@code WEAPON}, {@code ARMOR})
 */
public record GameItemReferenceDto(UUID id, String name, String kind) {}
