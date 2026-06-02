package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code BlueprintOverviewEntryDto} (#364): one row of the org-unit
 * blueprint availability overview — a distinct product plus how many in-scope members own it.
 *
 * @param productKey normalized product identity (the drill-down key)
 * @param productName display spelling of the product
 * @param ownerCount number of distinct in-scope members that own this blueprint
 */
public record BlueprintOverviewEntryDto(String productKey, String productName, long ownerCount) {}
