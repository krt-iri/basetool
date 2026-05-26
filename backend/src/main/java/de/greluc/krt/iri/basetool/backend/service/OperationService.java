package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.OperationPayoutStatus;
import de.greluc.krt.iri.basetool.backend.model.OperationStatus;
import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationPayoutStatusRepository;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus payout computation for the {@code operation} aggregate (one or more missions grouped
 * under a single umbrella).
 *
 * <p>Deletion intentionally does NOT cascade to missions — the missions stay alive as
 * operation-less rows so their participant / inventory / refinery history survives. The operation
 * table is small; no caching here, every method goes through the repository directly.
 *
 * <p>The status transition uses the state machine declared on {@code OperationStatus}; admins can
 * override the gate via {@code canOverrideStatus=true} (resolved at the controller boundary from
 * the {@code Authentication}).
 *
 * <p>The payout view is the only complex read path here: {@link #getOperationPayouts(UUID)}
 * computes the per-participant time share AND the actual money number per participant. The money
 * formula treats out-of-pocket expenses (mission EXPENSE entries owned by the participant +
 * refinery orders' costs where they are the owner) as a "reimbursement off the top" before the
 * remaining {@code totalSum} is split per participation percentage among PAYOUT participants.
 * DONATE participants still get their personal reimbursement (their own money) but their share is
 * not distributed. Finally, an in-game banking fee is deducted from every participant's gross
 * payout so the displayed amount matches what their mobiGlas will actually receive. The fee rate is
 * runtime-editable: it lives under the {@code operation.transfer_fee_rate} key in {@code
 * system_setting} (seeded to 0.005 = 0.5% by Flyway), can be tuned from {@code /admin/settings} by
 * officers and admins, and degrades to {@link #DEFAULT_TRANSFER_FEE_RATE} when the row is missing,
 * blank or unparseable. {@link #setPayoutStatus(UUID, String, boolean)} provides the
 * mission-manager toggle that records whether a participant has been paid out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OperationService {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  /**
   * Key under which the in-game banking fee rate is stored in {@code system_setting}. Seeded by
   * {@code V79__add_operation_transfer_fee_rate_setting.sql} with the documented default and
   * editable from {@code /admin/settings} by officers and admins.
   */
  static final String TRANSFER_FEE_RATE_SETTING_KEY = "operation.transfer_fee_rate";

  /**
   * Fallback in-game banking fee rate used when the {@code operation.transfer_fee_rate} system
   * setting is absent, blank or unparseable. Mirrors Star Citizen's documented Spectrum / mobiGlas
   * banking charge of 0.5% on every aUEC transfer to the recipient. Kept as a hard fallback so a
   * truncated or accidentally-deleted setting row degrades gracefully to the historical default
   * instead of crashing the payout view (which is gated behind authentication and used by mission
   * managers settling operations — silent zero would be far worse than a possibly-stale rate).
   */
  static final BigDecimal DEFAULT_TRANSFER_FEE_RATE = new BigDecimal("0.005");

  /**
   * Upper bound for the in-game banking fee rate. A value &gt;= 1 would deduct the entire payout
   * (or more), which is never a legitimate Star Citizen banking fee — reject defensively and fall
   * back to the documented default instead of zeroing out every payout silently.
   */
  private static final BigDecimal MAX_TRANSFER_FEE_RATE = BigDecimal.ONE;

  private final OperationRepository operationRepository;
  private final MissionRepository missionRepository;
  private final MissionFinanceEntryRepository financeEntryRepository;
  private final RefineryOrderRepository refineryOrderRepository;
  private final OperationPayoutStatusRepository payoutStatusRepository;
  private final UserService userService;
  private final SystemSettingService systemSettingService;
  private final OwnerScopeService ownerScopeService;

  /**
   * Returns paged operation list.
   *
   * @param pageable page request
   * @return paged operation list
   */
  public Page<Operation> getAllOperations(@NotNull Pageable pageable) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return operationRepository.findAllScoped(
        scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds(), pageable);
  }

  /**
   * Free-text + status + scope search across operations. Mirrors {@code
   * MissionService.searchMissions} within the limits of the operation aggregate: operations have no
   * {@code plannedStartTime} of their own (that field lives on the underlying missions), so the
   * missions' date-range filter has no meaningful equivalent here and is deliberately omitted.
   * Falls back to the full {@link OperationStatus} enum set when {@code status} is {@code null} or
   * empty - the SQL contract requires a non-empty list. The squadron scope is resolved through
   * {@link OwnerScopeService} (admin "all squadrons" mode resolves to {@code null}).
   *
   * @param query free-text name/description fragment, may be {@code null}
   * @param status status list (string names of {@link OperationStatus}); {@code null}/empty means
   *     all statuses
   * @param pageable page request
   * @return paged matching operations
   */
  @NotNull
  public Page<Operation> searchOperations(
      @Nullable String query, @Nullable List<String> status, @NotNull Pageable pageable) {
    List<String> effectiveStatus =
        (status == null || status.isEmpty())
            ? Arrays.stream(OperationStatus.values()).map(Enum::name).toList()
            : status;
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return operationRepository.searchOperations(
        query,
        effectiveStatus,
        scope.adminAllScope(),
        scope.activeOrgUnitId(),
        scope.memberOrgUnitIds(),
        pageable);
  }

  /**
   * Returns the slim id + name projection of every operation in the caller's squadron scope, sorted
   * by name. Used by the mission-detail page's operation-picker dropdown so the page render does
   * not need to pull the full {@code OperationDto} payload for every option.
   *
   * @return slim {@link de.greluc.krt.iri.basetool.backend.model.dto.OperationReferenceDto} list,
   *     filtered by the caller's squadron scope
   */
  @NotNull
  public java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationReferenceDto>
      findAllReference() {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return operationRepository.findAllReferenceScoped(
        scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds());
  }

  /**
   * Returns the operation.
   *
   * @param id operation primary key
   * @return the operation
   * @throws NotFoundException when no match
   */
  public Operation getOperationById(@NotNull UUID id) {
    return operationRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Operation not found"));
  }

  /**
   * Returns {@code true} if at least one mission of the operation still lacks an {@code
   * actualStartTime} or {@code actualEndTime}. The operation-detail page reads this flag to decide
   * whether to render the "payout figures are preliminary" warning above the payout table — the
   * payout breakdown silently skips missions without both timestamps (see {@link
   * #computeParticipationBreakdown(Operation)}), so percentages can rebalance once every mission is
   * properly closed.
   *
   * <p>Implemented as a single existence-style {@code COUNT > 0} query on {@code Mission} so the
   * detail endpoint only pays one cheap round-trip on top of the existing {@code findById} hit; no
   * lazy collection traversal on the operation graph.
   *
   * @param id operation primary key
   * @return {@code true} when at least one mission has a {@code null actualStartTime} or {@code
   *     actualEndTime}, {@code false} when every mission is fully time-stamped (including the
   *     empty-operation case)
   */
  public boolean hasUnfinishedMissions(@NotNull UUID id) {
    return missionRepository.existsByOperationIdWithUnfinishedActualTime(id);
  }

  /**
   * Persists a new operation. R5.d.e routes the owning-Staffel stamp through the shared picker
   * resolver when an authenticated caller is on the security context.
   *
   * <ul>
   *   <li>Caller resolved AND {@code owningOrgUnitId} provided → resolver validates the picked org
   *       unit against the caller's memberships and stamps the corresponding Staffel (rejecting
   *       Spezialkommando selections with {@code BadRequestException} until the cleanup release
   *       lifts the NOT NULL on {@code owning_squadron_id}).
   *   <li>Caller resolved AND {@code owningOrgUnitId} is {@code null} → resolver falls back to the
   *       caller's home Staffel via {@code User.getSquadron()}. Functionally identical to the
   *       legacy "stamp from active scope" path for the common single-membership case.
   *   <li>No authenticated caller (admin in "all squadrons" mode, anonymous fallback) → preserve
   *       the historical {@code OwnerScopeService.currentSquadron()} path. The picker UUID, if
   *       supplied, cannot be membership-validated without a user, so it is ignored.
   * </ul>
   *
   * @param operation transient entity
   * @param owningOrgUnitId optional picker output from {@link
   *     de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto#owningOrgUnitId}; {@code
   *     null} for the legacy implicit-scope path.
   * @return the persisted operation
   */
  @Transactional
  public Operation createOperation(@NotNull Operation operation, @Nullable UUID owningOrgUnitId) {
    if (operation.getOwningSquadron() == null) {
      User caller = userService.getCurrentUser().orElse(null);
      if (caller != null) {
        operation.setOwningSquadron(
            ownerScopeService.resolveSquadronForPickerOutput(caller, owningOrgUnitId));
      } else {
        ownerScopeService.currentSquadron().ifPresent(operation::setOwningSquadron);
      }
    }
    return operationRepository.save(operation);
  }

  /**
   * Updates an existing operation. Validates the optimistic-lock version and the status state
   * machine (unless the caller has the admin override).
   *
   * @param id operation primary key
   * @param updateDto update payload (carries the expected version + new status)
   * @param canOverrideStatus when true, the state-machine check is bypassed (admin/officer)
   * @return the persisted operation
   * @throws ObjectOptimisticLockingFailureException when the supplied version is stale
   * @throws BadRequestException when the status transition is invalid and override is not granted
   */
  @Transactional
  public Operation updateOperation(
      @NotNull UUID id, @NotNull OperationUpdateDto updateDto, boolean canOverrideStatus) {
    Operation operation =
        operationRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Operation not found"));

    if (updateDto.version() != null && !operation.getVersion().equals(updateDto.version())) {
      throw new ObjectOptimisticLockingFailureException(Operation.class, id);
    }

    if (!canOverrideStatus && !operation.getStatus().canTransitionTo(updateDto.status())) {
      throw new BadRequestException(
          "Invalid operation status transition: "
              + operation.getStatus()
              + " -> "
              + updateDto.status());
    }

    operation.setName(updateDto.name());
    operation.setDescription(updateDto.description());
    operation.setStatus(updateDto.status());

    return operationRepository.save(operation);
  }

  /**
   * Deletes an operation without deleting its missions.
   *
   * <p>Each linked mission has its {@code operation} reference cleared (Hibernate dirty-checking
   * emits a single {@code UPDATE} per mission). The in-memory collection is cleared explicitly so
   * the bidirectional state stays consistent inside the transaction; the operation row is then
   * removed. Participants, finance entries, inventory items and refinery orders of the underlying
   * missions are untouched — this delete is purely a "ungroup" action.
   *
   * @param id operation primary key
   * @throws NotFoundException when no match
   */
  @Transactional
  public void deleteOperation(@NotNull UUID id) {
    log.info("Deleting operation with ID: {}", id);
    Operation operation =
        operationRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Operation not found"));

    // Unlink missions instead of cascading the delete. The mission itself,
    // its participants, finance entries, inventory items and refinery orders
    // all stay intact — only the operation_id back-reference is cleared so
    // the rows can survive as operation-less missions.
    for (Mission mission : operation.getMissions()) {
      mission.setOperation(null);
    }
    operation.getMissions().clear();

    operationRepository.delete(operation);
    log.info("Successfully deleted operation with ID: {}", id);
  }

  /**
   * Computes the per-participant payout breakdown for the operation.
   *
   * <p>Pulls the operation with its missions / participants pre-fetched, then loads finance entries
   * and refinery orders by mission id so the percentage AND the money number can be derived in one
   * pass. The percentage is the participant's clamped attendance time over the operation's clamped
   * attendance time (mirrors the previous behavior); the money number is {@code
   * round(personalExpenses + sharePayout − transferFee)} — final HALF_UP rounding to whole aUEC,
   * because Star Citizen's mobiGlas does not accept fractional credits in a transfer. The
   * components below are tracked at two-decimal precision so the breakdown sub-labels stay
   * auditable; only the final {@code payoutAmount} is collapsed to scale 0:
   *
   * <ul>
   *   <li><b>personalExpenses</b> reimburses each participant for the expenses attributed to them —
   *       mission EXPENSE entries where they are the {@code participant} plus refinery orders'
   *       {@code expenses + otherExpenses} where they are the {@code owner}.
   *   <li><b>sharePayout</b> is {@code totalSum × percentage / 100} for PAYOUT participants and
   *       {@link BigDecimal#ZERO} for DONATE participants. Their share is contributed to the org
   *       but the reimbursement is still paid out (it is the participant's own money returned).
   *   <li><b>transferFee</b> is {@code (personalExpenses + sharePayout) × transferFeeRate} — Star
   *       Citizen's in-game banking deducts a small fraction from any aUEC transfer to the
   *       recipient, so this fee is subtracted from the gross payout to show what actually lands in
   *       the participant's mobiGlas. The rate is runtime-editable via the {@code
   *       operation.transfer_fee_rate} system setting (default 0.005 = 0.5%, fallback applied when
   *       the row is absent or unparseable). Applies to PAYOUT and DONATE participants alike
   *       (DONATE participants only receive their reimbursement, but that transfer also incurs the
   *       fee).
   * </ul>
   *
   * <p>The {@code paidOut*} fields are merged in from {@link OperationPayoutStatus} rows by
   * participant key; absence of a row is treated as {@code paidOut=false}. Output is sorted
   * alphabetically by participant name.
   *
   * <p>Uses {@link OperationRepository#findWithMissionsAndParticipantsById} with an explicit
   * {@code @EntityGraph} so the loop's {@code .getMissions()/.getParticipants()/.getUser()} never
   * triggers lazy SELECTs — otherwise {@code 1 + N + N*M} round trips for {@code N} missions ×
   * {@code M} participants each. Refinery orders are fetched via {@code @EntityGraph} on the
   * repository method so the owner is eagerly loaded for the per-owner cost attribution.
   *
   * @param id operation primary key
   * @return per-participant payout breakdown, sorted by participant name
   * @throws NotFoundException when no match
   */
  public List<OperationPayoutDto> getOperationPayouts(@NotNull UUID id) {
    Operation operation =
        operationRepository
            .findWithMissionsAndParticipantsById(id)
            .orElseThrow(() -> new NotFoundException("Operation not found"));

    List<UUID> missionIds = operation.getMissions().stream().map(Mission::getId).toList();
    List<MissionFinanceEntry> allEntries =
        missionIds.isEmpty() ? List.of() : financeEntryRepository.findAllByMissionIdIn(missionIds);
    List<RefineryOrder> allOrders =
        missionIds.isEmpty() ? List.of() : refineryOrderRepository.findByMissionIdIn(missionIds);

    BigDecimal totalSum = computeTotalSum(allEntries, allOrders);
    Map<String, BigDecimal> personalExpensesByKey =
        computePersonalExpensesByParticipant(allEntries, allOrders);
    BigDecimal transferFeeRate = resolveTransferFeeRate();

    ParticipationBreakdown breakdown = computeParticipationBreakdown(operation);

    Map<String, OperationPayoutStatus> statusByKey =
        payoutStatusRepository.findByOperationId(id).stream()
            .collect(
                Collectors.toMap(OperationPayoutStatus::getParticipantKey, Function.identity()));

    List<OperationPayoutDto> result = new ArrayList<>(breakdown.participantNames.size());
    for (Map.Entry<String, String> participant : breakdown.participantNames.entrySet()) {
      String key = participant.getKey();
      long duration = breakdown.validDurations.getOrDefault(key, 0L);
      double percentage =
          breakdown.totalDuration > 0 ? (double) duration / breakdown.totalDuration * 100.0 : 0.0;
      percentage = Math.round(percentage * 100.0) / 100.0;

      PayoutPreference pref = breakdown.preferences.getOrDefault(key, PayoutPreference.PAYOUT);
      BigDecimal personalExpenses =
          personalExpensesByKey
              .getOrDefault(key, BigDecimal.ZERO)
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal shareAmount =
          pref == PayoutPreference.DONATE
              ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
              : totalSum
                  .multiply(BigDecimal.valueOf(percentage))
                  .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
      BigDecimal grossPayout = personalExpenses.add(shareAmount);
      // Star Citizen banking deducts a small percentage from any aUEC transfer to the recipient —
      // model that here so the displayed Auszahlungsbetrag is what actually lands in the
      // participant's mobiGlas. The rate is loaded once per call from system_setting (see
      // resolveTransferFeeRate) so officers/admins can adjust it without a redeploy. Rounded
      // HALF_UP to match the other monetary fields. Negative gross would produce a negative fee
      // mathematically; in practice grossPayout is always >= 0 (expenses are positive, share is
      // >= 0), so HALF_UP rounding here mirrors the rest of the pipeline.
      BigDecimal transferFee =
          grossPayout.multiply(transferFeeRate).setScale(2, RoundingMode.HALF_UP);
      // Kaufmaennisch (HALF_UP) auf ganze aUEC runden: Star Citizen kennt im mobiGlas keine
      // Nachkommastellen, jeder Transfer landet als Integer beim Empfaenger. Damit der angezeigte
      // Auszahlungsbetrag dem entspricht, was der Mission Manager tatsaechlich anweisen muss,
      // rundet das Backend hier final auf scale 0. Die Sub-Labels (Auslagen, Ueberweisungsgebuehr)
      // behalten ihre 2 Nachkommastellen — sie bleiben transparenter Breakdown, nicht der "Pay X"-
      // Betrag — was geringfuegige Diskrepanzen beim Nachrechnen erlaubt, dafuer aber den Hauptwert
      // glatt haelt.
      BigDecimal payoutAmount = grossPayout.subtract(transferFee).setScale(0, RoundingMode.HALF_UP);

      OperationPayoutStatus status = statusByKey.get(key);
      boolean paidOut = status != null && status.isPaidOut();
      Instant paidOutAt = status != null ? status.getPaidOutAt() : null;
      String paidOutByName =
          status != null && status.getPaidOutByUser() != null
              ? status.getPaidOutByUser().getEffectiveName()
              : null;

      result.add(
          new OperationPayoutDto(
              key,
              participant.getValue(),
              percentage,
              pref,
              personalExpenses,
              shareAmount,
              transferFee,
              payoutAmount,
              paidOut,
              paidOutAt,
              paidOutByName));
    }

    result.sort(
        Comparator.comparing(OperationPayoutDto::participantName, String.CASE_INSENSITIVE_ORDER));
    return result;
  }

  /**
   * Toggles the paid-out flag for a single participant of an operation, recording the acting user
   * and timestamp. Materializes a new {@link OperationPayoutStatus} row on the first toggle;
   * subsequent toggles update the row in place. Last-writer-wins: no client-supplied version is
   * required because the field is a boolean and concurrent toggles are intrinsically idempotent —
   * repeated calls with the same value still refresh the {@code paidOutAt} / {@code paidOutByUser}
   * audit trail so the most recent click is always recorded.
   *
   * @param operationId operation primary key
   * @param participantKey opaque participant key produced by {@link #getOperationPayouts}
   * @param paidOut new flag value
   * @return the freshly-rendered payout DTO for the updated participant, suitable for replacing a
   *     single row in the caller's table
   * @throws NotFoundException when the operation does not exist or the participant key cannot be
   *     resolved against the operation's current participant list
   */
  @Transactional
  public OperationPayoutDto setPayoutStatus(
      @NotNull UUID operationId, @NotNull String participantKey, boolean paidOut) {
    // Verify operation exists (cheap existence check — avoid loading the full graph here).
    if (!operationRepository.existsById(operationId)) {
      throw new NotFoundException("Operation not found");
    }

    Optional<OperationPayoutStatus> existing =
        payoutStatusRepository.findByOperationIdAndParticipantKey(operationId, participantKey);
    OperationPayoutStatus status =
        existing.orElseGet(
            () -> {
              OperationPayoutStatus s = new OperationPayoutStatus();
              s.setOperation(operationRepository.getReferenceById(operationId));
              s.setParticipantKey(participantKey);
              return s;
            });

    status.setPaidOut(paidOut);
    if (paidOut) {
      status.setPaidOutAt(Instant.now());
      User actor = userService.getCurrentUser().orElse(null);
      status.setPaidOutByUser(actor);
    }
    // Note: when toggling back to paidOut=false we deliberately keep paidOutAt / paidOutByUser as
    // the last "was paid" trace — see V78 migration's column comments. The frontend renders the
    // current paidOut flag, the audit fields are only inspected when paidOut=true.

    payoutStatusRepository.save(status);

    // Re-render the full row so the caller can patch its DOM without a second round-trip. We use
    // the same canonical path as the read endpoint to guarantee the displayed amount stays in
    // lock-step with what the backend just persisted.
    return getOperationPayouts(operationId).stream()
        .filter(dto -> participantKey.equals(dto.participantId()))
        .findFirst()
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Participant '"
                        + participantKey
                        + "' is not part of operation "
                        + operationId));
  }

  /**
   * Loads and validates the runtime-editable in-game transfer-fee rate from {@code system_setting}.
   * Falls back to {@link #DEFAULT_TRANSFER_FEE_RATE} when the row is absent, blank, not a number,
   * negative or {@code >= 1} (which would zero-out or invert every payout). The fallback is logged
   * at WARN so an operator sees the misconfiguration in the access log instead of silently working
   * off a stale default. Called once per {@link #getOperationPayouts(UUID)} invocation — the
   * underlying repository hit is a single PK lookup on a tiny table, so caching is unnecessary and
   * would only introduce stale-value risk after admin edits.
   *
   * @return a non-null, validated rate in the range {@code [0, 1)}; either the persisted setting
   *     value or {@link #DEFAULT_TRANSFER_FEE_RATE} when the persisted value is unusable
   */
  @NotNull
  private BigDecimal resolveTransferFeeRate() {
    Optional<String> raw = systemSettingService.getSettingValue(TRANSFER_FEE_RATE_SETTING_KEY);
    if (raw.isEmpty() || raw.get().isBlank()) {
      log.warn(
          "System setting '{}' is missing or blank, falling back to default {}",
          TRANSFER_FEE_RATE_SETTING_KEY,
          DEFAULT_TRANSFER_FEE_RATE);
      return DEFAULT_TRANSFER_FEE_RATE;
    }
    try {
      BigDecimal parsed = new BigDecimal(raw.get().trim());
      if (parsed.signum() < 0 || parsed.compareTo(MAX_TRANSFER_FEE_RATE) >= 0) {
        log.warn(
            "System setting '{}'={} is out of range [0, 1), falling back to default {}",
            TRANSFER_FEE_RATE_SETTING_KEY,
            parsed,
            DEFAULT_TRANSFER_FEE_RATE);
        return DEFAULT_TRANSFER_FEE_RATE;
      }
      return parsed;
    } catch (NumberFormatException e) {
      log.warn(
          "System setting '{}'='{}' is not a valid decimal, falling back to default {}",
          TRANSFER_FEE_RATE_SETTING_KEY,
          raw.get(),
          DEFAULT_TRANSFER_FEE_RATE);
      return DEFAULT_TRANSFER_FEE_RATE;
    }
  }

  /**
   * Computes the operation total sum identically to {@code OperationFinanceService} (mission INCOME
   * plus refinery profit minus mission EXPENSE). Kept private because the canonical implementation
   * lives in {@code OperationFinanceService}; duplicating ten lines here avoids pulling that
   * service onto the payout call path with its own DTO assembly cost.
   */
  @NotNull
  private static BigDecimal computeTotalSum(
      @NotNull List<MissionFinanceEntry> entries, @NotNull List<RefineryOrder> orders) {
    BigDecimal total = BigDecimal.ZERO;
    for (MissionFinanceEntry entry : entries) {
      if (entry.getType() == FinanceType.INCOME) {
        total = total.add(entry.getAmount());
      } else if (entry.getType() == FinanceType.EXPENSE) {
        total = total.subtract(entry.getAmount());
      }
    }
    for (RefineryOrder order : orders) {
      double sales = order.getOreSales() != null ? order.getOreSales() : 0d;
      double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
      double otherCosts = order.getOtherExpenses() != null ? order.getOtherExpenses() : 0d;
      double profit = sales - costs - otherCosts;
      if (profit != 0d) {
        total = total.add(BigDecimal.valueOf(profit));
      }
    }
    return total;
  }

  /**
   * Sums each participant's out-of-pocket expenses across the whole operation: mission EXPENSE
   * entries where they are the {@code participant} plus refinery orders' {@code expenses +
   * otherExpenses} where they are the {@code owner}. Refinery sales accrue to the operation pool
   * (see {@code computeTotalSum}) — the owner only gets back the costs they advanced. INCOME
   * entries are not attributed to any single participant; they belong to the shared pool.
   */
  @NotNull
  private static Map<String, BigDecimal> computePersonalExpensesByParticipant(
      @NotNull List<MissionFinanceEntry> entries, @NotNull List<RefineryOrder> orders) {
    Map<String, BigDecimal> byKey = new HashMap<>();
    for (MissionFinanceEntry entry : entries) {
      if (entry.getType() != FinanceType.EXPENSE) {
        continue;
      }
      MissionParticipant participant = entry.getParticipant();
      if (participant == null) {
        continue;
      }
      String key = participantKey(participant);
      if (key == null) {
        continue;
      }
      byKey.merge(key, entry.getAmount(), BigDecimal::add);
    }
    for (RefineryOrder order : orders) {
      if (order.getOwner() == null) {
        continue;
      }
      double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
      double otherCosts = order.getOtherExpenses() != null ? order.getOtherExpenses() : 0d;
      double total = costs + otherCosts;
      if (total <= 0d) {
        continue;
      }
      String key = order.getOwner().getId().toString();
      byKey.merge(key, BigDecimal.valueOf(total), BigDecimal::add);
    }
    return byKey;
  }

  /**
   * Builds the duration / preference / display-name maps in one pass over the operation's
   * mission-participant graph. Missions without both actualStart and actualEnd are skipped (they
   * have no defined attendance window). DONATE is sticky across the operation: a single DONATE
   * preference on any mission marks the participant as donating for the whole operation, matching
   * the previous {@code OperationService} behavior.
   */
  @NotNull
  private static ParticipationBreakdown computeParticipationBreakdown(
      @NotNull Operation operation) {
    Map<String, String> participantNames = new HashMap<>();
    Map<String, Long> validDurations = new HashMap<>();
    Map<String, PayoutPreference> preferences = new HashMap<>();
    long totalDuration = 0L;

    for (Mission mission : operation.getMissions()) {
      Instant actualStart = mission.getActualStartTime();
      Instant actualEnd = mission.getActualEndTime();
      if (actualStart == null || actualEnd == null || !actualEnd.isAfter(actualStart)) {
        continue;
      }

      for (MissionParticipant p : mission.getParticipants()) {
        String key = participantKey(p);
        if (key == null) {
          continue;
        }

        String name = p.getUser() != null ? p.getUser().getEffectiveName() : p.getGuestName();
        participantNames.putIfAbsent(key, name);

        PayoutPreference current = preferences.getOrDefault(key, PayoutPreference.PAYOUT);
        if (p.getPayoutPreference() == PayoutPreference.DONATE) {
          preferences.put(key, PayoutPreference.DONATE);
        } else {
          preferences.putIfAbsent(key, current);
        }

        Instant participantStart = p.getStartTime();
        if (participantStart == null) {
          continue;
        }
        Instant participantEnd = p.getEndTime() != null ? p.getEndTime() : Instant.now();

        Instant effectiveStart =
            participantStart.isBefore(actualStart) ? actualStart : participantStart;
        Instant effectiveEnd = participantEnd.isAfter(actualEnd) ? actualEnd : participantEnd;

        if (effectiveEnd.isAfter(effectiveStart)) {
          long duration = Duration.between(effectiveStart, effectiveEnd).toMillis();
          validDurations.merge(key, duration, Long::sum);
          totalDuration += duration;
        }
      }
    }

    return new ParticipationBreakdown(participantNames, validDurations, preferences, totalDuration);
  }

  /**
   * Returns the opaque participant key used across the payout pipeline — real user UUID
   * stringified, or {@code "guest_<name>"} for guests. Returns {@code null} when neither a user nor
   * a guest name is set (a malformed row that should be filtered out).
   */
  @Nullable
  private static String participantKey(@NotNull MissionParticipant participant) {
    if (participant.getUser() != null) {
      return participant.getUser().getId().toString();
    }
    if (participant.getGuestName() != null) {
      return "guest_" + participant.getGuestName();
    }
    return null;
  }

  /**
   * Internal carrier for the values produced by a single pass over the operation's
   * mission-participant graph. Keeping them as a record avoids passing four separate maps around
   * and accidentally desyncing them.
   */
  private record ParticipationBreakdown(
      Map<String, String> participantNames,
      Map<String, Long> validDurations,
      Map<String, PayoutPreference> preferences,
      long totalDuration) {}
}
