package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of one Kommando(gruppe) within a Staffel. The Kommando is its own row, so {@link
 * #positionId} / {@link #version} are the handle the inline editor uses to rename it, remove it, or
 * assign / reassign its Kommandoleiter. The Kommandoleiter lives on that row and is carried inline
 * ({@link #leaderUserId} / {@link #leaderUserName}, both {@code null} while the seat is vacant).
 *
 * @param positionId id of the Kommando row; handle for rename / remove / assign-lead / add-child.
 * @param name the Kommando's display name, or {@code null} when unnamed (template shows a
 *     fallback).
 * @param version optimistic-lock version of the Kommando row, echoed back on rename / assign-lead.
 * @param sortIndex stable display order among the Staffel's Kommandos.
 * @param leaderUserId id of the Kommandoleiter, or {@code null} when the seat is vacant.
 * @param leaderUserName the Kommandoleiter's effective display name, or {@code null} when vacant.
 * @param deputy the Stv. Kommandoleiter node, or {@code null} when vacant.
 * @param ensigns the Ensigns reporting into this Kommando; never {@code null}, possibly empty.
 */
public record CommandChartDto(
    UUID positionId,
    String name,
    Long version,
    int sortIndex,
    UUID leaderUserId,
    String leaderUserName,
    OrgChartNodeDto deputy,
    List<OrgChartNodeDto> ensigns) {}
