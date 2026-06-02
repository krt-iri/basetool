package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * One row of the org-unit blueprint availability overview (#364): a distinct blueprint product plus
 * how many in-scope org-unit members own it. Carries no owner identity — the owner list is fetched
 * separately via the drill-down endpoint, and only by callers cleared for the overview.
 *
 * @param productKey the normalized product identity (the aggregation and drill-down key)
 * @param productName the display spelling of the product
 * @param ownerCount the number of distinct in-scope members that own this blueprint
 */
public record BlueprintOverviewEntryDto(String productKey, String productName, long ownerCount) {}
