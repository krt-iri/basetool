package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend SPEZIALKOMMANDO_PLAN.md §7.4 single-POST membership-delta wire
 * shape. Lives in {@code PATCH /api/v1/users/{id}/memberships}. Two-part payload: a single {@link
 * StaffelChange} record (a user has at most one Staffel membership) plus a list of {@link
 * SpecialCommandChange} records.
 *
 * <p>Wire contract is identical to the backend record field-for-field — per the {@code
 * feedback_backend_frontend_dto_mirror} rule. Any change here MUST land on the backend record in
 * the same commit (or vice versa).
 *
 * @param staffel Staffel-side delta, or {@code null} to leave the user's Staffel untouched.
 * @param specialCommands list of SK-side changes; may be {@code null} or empty.
 */
public record MembershipDeltaRequest(
    @Nullable StaffelChange staffel, @Nullable List<SpecialCommandChange> specialCommands) {

  /**
   * Staffel-side delta record. {@link #squadronId} {@code null} means "remove the Staffel
   * membership"; differing from the current Staffel means "reassign"; matching means "flag-only
   * patch".
   *
   * @param squadronId target Squadron id, or {@code null} to clear.
   * @param isLogistician new Logistician flag, or {@code null} to leave unchanged.
   * @param isMissionManager new Mission Manager flag, or {@code null} to leave unchanged.
   * @param userVersion the {@code app_user} row {@code @Version} for the optimistic-lock check on
   *     the assignment change; required when reassigning, optional for flag-only.
   */
  public record StaffelChange(
      @Nullable UUID squadronId,
      @Nullable Boolean isLogistician,
      @Nullable Boolean isMissionManager,
      @Nullable Long userVersion) {}

  /**
   * SK-side change record. {@link #action} picks between ADD (new membership), REMOVE (delete
   * existing), PATCH (flag update with optimistic-lock).
   *
   * @param orgUnitId SpecialCommand id this entry targets.
   * @param action ADD / REMOVE / PATCH.
   * @param isLogistician new flag value (ADD or PATCH).
   * @param isMissionManager new flag value (ADD or PATCH).
   * @param version {@code @Version} of the membership row (PATCH only).
   */
  public record SpecialCommandChange(
      UUID orgUnitId,
      Action action,
      @Nullable Boolean isLogistician,
      @Nullable Boolean isMissionManager,
      @Nullable Long version) {

    /** Action discriminator. */
    public enum Action {
      /** Create a new membership row. */
      ADD,
      /** Delete an existing membership row. */
      REMOVE,
      /** Update flags on an existing membership row. */
      PATCH
    }
  }
}
