/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.BankConflictException;
import de.greluc.krt.profit.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.mapper.BankAccountMapper;
import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.profit.basetool.backend.model.BankTransactionType;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.dto.BankAccountDetailDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankAccountDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankBookingDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankHolderBalanceDto;
import de.greluc.krt.profit.basetool.backend.model.dto.request.BankAccountLifecycleRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.CreateBankAccountRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.request.RenameBankAccountRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.model.projection.BankBookingRow;
import de.greluc.krt.profit.basetool.backend.model.projection.BankCounterLeg;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle and read surface of the Kartell bank (epic #556, REQ-BANK-001/-002): create,
 * rename, close, reopen plus the paged listings, the detail aggregate and the booking history.
 * Balances are always computed on read from the ledger (ADR-0010) and joined in batch-wise — never
 * per account (REQ-DATA-003).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankAccountService {

  private static final Duration DELTA_WINDOW = Duration.ofDays(30);

  private final BankAccountRepository accountRepository;
  private final BankPostingRepository postingRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final BankAccountMapper bankAccountMapper;
  private final BankAuditService bankAuditService;
  private final BankBookingRequestService bankBookingRequestService;

  /**
   * Pages over the accounts the caller may see: management/admin get all accounts, employees get
   * exactly their granted accounts (REQ-BANK-010). Balances are joined in one grouped query.
   *
   * @param management whether the caller has the management perspective
   * @param userId the caller's user id (used for the employee filter)
   * @param pageable page, size and whitelisted sort
   * @return one page of accounts incl. balances
   */
  public Page<BankAccountDto> getAccounts(
      boolean management, @NotNull UUID userId, @NotNull Pageable pageable) {
    Page<BankAccount> page =
        management
            ? accountRepository.findAll(pageable)
            : accountRepository.findGrantedTo(userId, pageable);
    Map<UUID, BigDecimal> balances =
        balancesFor(page.getContent().stream().map(BankAccount::getId).toList());
    return page.map(
        account ->
            bankAccountMapper.toDto(
                account, balances.getOrDefault(account.getId(), BigDecimal.ZERO)));
  }

  /**
   * Loads the detail aggregate of one account (K1 mockup): account + balance, 30-day delta,
   * facts-strip counts, the permanent holder distribution (REQ-BANK-003) and the caller's
   * capabilities. Visibility is gated at the controller via {@code BankSecurityService.canSee}; the
   * controller also evaluates and passes the capability flags so this service stays free of
   * authentication concerns.
   *
   * @param accountId the account
   * @param capabilities the caller's evaluated capabilities on the account
   * @return the detail payload
   * @throws NotFoundException when the account does not exist
   */
  public BankAccountDetailDto getAccountDetail(
      @NotNull UUID accountId, @NotNull BankCapabilitiesDto capabilities) {
    BankAccount account = requireAccount(accountId);
    BigDecimal balance = postingRepository.accountBalance(accountId);
    Instant cutoff = Instant.now().minus(DELTA_WINDOW);
    BigDecimal delta =
        postingRepository.postingSlicesSince(List.of(accountId), cutoff).stream()
            .map(slice -> slice.amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new BankAccountDetailDto(
        bankAccountMapper.toDto(account, balance),
        delta,
        postingRepository.countByAccountId(accountId),
        postingRepository.countDistinctHoldersByAccountId(accountId),
        getHolderDistribution(accountId),
        capabilities);
  }

  /**
   * The account's non-zero holder sub-balances (REQ-BANK-003), largest stash first.
   *
   * @param accountId the account
   * @return the distribution slices
   */
  public List<BankHolderBalanceDto> getHolderDistribution(@NotNull UUID accountId) {
    return postingRepository.holderDistribution(accountId).stream()
        .filter(h -> h.amount().signum() != 0)
        .map(h -> new BankHolderBalanceDto(h.holderId(), h.handle(), h.holderActive(), h.amount()))
        .toList();
  }

  /**
   * Pages over one account's booking history with the transfer counter-legs resolved in one batched
   * IN-query (no per-row lookups, REQ-BANK-018).
   *
   * @param accountId the account
   * @param pageable page, size and whitelisted sort (default newest first)
   * @return one page of booking rows
   */
  public Page<BankBookingDto> getBookings(@NotNull UUID accountId, @NotNull Pageable pageable) {
    requireAccount(accountId);
    Page<BankBookingRow> rows = postingRepository.findBookings(accountId, pageable);
    List<UUID> transferTxIds =
        rows.getContent().stream()
            .filter(r -> r.type() == BankTransactionType.TRANSFER)
            .map(BankBookingRow::transactionId)
            .distinct()
            .toList();
    Map<UUID, List<BankCounterLeg>> legsByTx =
        transferTxIds.isEmpty()
            ? Map.of()
            : postingRepository.findLegsByTransactionIds(transferTxIds).stream()
                .collect(Collectors.groupingBy(BankCounterLeg::transactionId));
    return rows.map(row -> toBookingDto(accountId, row, legsByTx));
  }

  /**
   * Creates an account (REQ-BANK-001/-002): validates the type-specific owner reference, enforces
   * the singleton/per-org-unit uniqueness with clean 409s, draws the next {@code KB-} number and
   * audits the creation. All account types — including the two singletons — are created at runtime
   * through this path; nothing is seeded by migration.
   *
   * @param request validated creation payload
   * @return the created account incl. its (zero) balance
   * @throws BadRequestException when the owner reference does not match the type
   * @throws DuplicateEntityException when the singleton or per-org-unit uniqueness is violated
   * @throws NotFoundException when the referenced org unit does not exist
   */
  @Transactional
  public BankAccountDto createAccount(@NotNull CreateBankAccountRequest request) {
    BankAccount account = new BankAccount();
    account.setName(request.name().trim());
    account.setType(request.type());
    account.setStatus(BankAccountStatus.ACTIVE);
    switch (request.type()) {
      case ORG_UNIT -> {
        if (request.orgUnitId() == null) {
          throw new BadRequestException("An ORG_UNIT account requires an org unit reference");
        }
        if (request.areaName() != null && !request.areaName().isBlank()) {
          throw new BadRequestException("An ORG_UNIT account must not carry an area name");
        }
        OrgUnit orgUnit =
            orgUnitRepository
                .findById(request.orgUnitId())
                .orElseThrow(() -> new NotFoundException("Org unit not found"));
        // Epic #692 Phase 6 (REQ-ORG-019): since Bereich/OL are first-class org_unit rows now, an
        // ORG_UNIT account must reference a Staffel/SK — a Bereich is an AREA account and the OL
        // the
        // CARTEL account. Symmetric with the BEREICH/ORGANISATIONSLEITUNG kind guards below;
        // without
        // it an ORG_UNIT account on a Bereich/OL would consume that unit's one-account slot.
        if (orgUnit.getKind() != OrgUnitKind.SQUADRON
            && orgUnit.getKind() != OrgUnitKind.SPECIAL_COMMAND) {
          throw new BadRequestException(
              "An ORG_UNIT account must reference a Staffel or Spezialkommando");
        }
        if (accountRepository.existsByOrgUnitId(orgUnit.getId())) {
          throw new DuplicateEntityException("The org unit already owns a bank account");
        }
        account.setOrgUnit(orgUnit);
      }
      case AREA -> {
        // Epic #692 (REQ-ORG-019, V168): an AREA account is owned by its Bereich via the org_unit
        // FK, not the legacy free-form area name. The Bereich is a first-class org unit now.
        if (request.orgUnitId() == null) {
          throw new BadRequestException("An AREA account requires its Bereich org unit");
        }
        requireNoAreaName(request);
        OrgUnit bereich =
            orgUnitRepository
                .findById(request.orgUnitId())
                .orElseThrow(() -> new NotFoundException("Org unit not found"));
        if (bereich.getKind() != OrgUnitKind.BEREICH) {
          throw new BadRequestException("An AREA account must reference a Bereich org unit");
        }
        if (accountRepository.existsByOrgUnitId(bereich.getId())) {
          throw new DuplicateEntityException("The Bereich already owns a bank account");
        }
        account.setOrgUnit(bereich);
      }
      case CARTEL -> {
        // Epic #692: the singleton CARTEL account is mapped to the Organisationsleitung via the
        // org_unit FK so an OL member's oversight scope reaches it. The link is optional (a CARTEL
        // may predate the OL); when supplied it must be the OL and uniqueness is enforced.
        requireNoAreaName(request);
        if (accountRepository.existsByType(request.type())) {
          throw new DuplicateEntityException(
              "The " + request.type() + " account already exists (singleton)");
        }
        if (request.orgUnitId() != null) {
          OrgUnit ol =
              orgUnitRepository
                  .findById(request.orgUnitId())
                  .orElseThrow(() -> new NotFoundException("Org unit not found"));
          if (ol.getKind() != OrgUnitKind.ORGANISATIONSLEITUNG) {
            throw new BadRequestException(
                "The CARTEL account must reference the Organisationsleitung");
          }
          if (accountRepository.existsByOrgUnitId(ol.getId())) {
            throw new DuplicateEntityException(
                "The Organisationsleitung already owns a bank account");
          }
          account.setOrgUnit(ol);
        }
      }
      case CARTEL_BANK -> {
        requireNoOrgUnit(request);
        requireNoAreaName(request);
        if (accountRepository.existsByType(request.type())) {
          throw new DuplicateEntityException(
              "The " + request.type() + " account already exists (singleton)");
        }
      }
      case SPECIAL -> {
        requireNoOrgUnit(request);
        requireNoAreaName(request);
      }
      default -> throw new BadRequestException("Unsupported bank account type: " + request.type());
    }
    account.setAccountNo(String.format("KB-%04d", accountRepository.nextAccountNoValue()));
    BankAccount saved = accountRepository.save(account);
    bankAuditService.record(
        BankAuditEventType.ACCOUNT_CREATED,
        saved.getId(),
        null,
        null,
        saved.getAccountNo() + " " + saved.getName() + " (" + saved.getType() + ")");
    return bankAccountMapper.toDto(saved, BigDecimal.ZERO);
  }

  /**
   * Renames an account; the only mutable attribute outside the lifecycle (REQ-BANK-001).
   *
   * @param accountId the account
   * @param request the new name plus the echoed optimistic-locking version
   * @return the updated account incl. its balance
   * @throws NotFoundException when the account does not exist
   * @throws ObjectOptimisticLockingFailureException on a version mismatch (409)
   */
  @Transactional
  public BankAccountDto renameAccount(
      @NotNull UUID accountId, @NotNull RenameBankAccountRequest request) {
    BankAccount account = requireAccount(accountId);
    requireVersionMatch(account, request.version());
    String oldName = account.getName();
    account.setName(request.name().trim());
    BankAccount saved = accountRepository.save(account);
    bankAuditService.record(
        BankAuditEventType.ACCOUNT_RENAMED,
        saved.getId(),
        null,
        null,
        "'" + oldName + "' -> '" + saved.getName() + "'");
    return bankAccountMapper.toDto(saved, postingRepository.accountBalance(accountId));
  }

  /**
   * Closes an account (REQ-BANK-002): requires a zero balance — transfer the remainder first. The
   * closed account stays fully readable with its history; only postings are rejected.
   *
   * @param accountId the account
   * @param request the echoed optimistic-locking version
   * @return the updated account
   * @throws NotFoundException when the account does not exist
   * @throws BankConflictException with {@code BANK_ACCOUNT_NOT_EMPTY} on a non-zero balance
   * @throws ObjectOptimisticLockingFailureException on a version mismatch (409)
   */
  @Transactional
  public BankAccountDto closeAccount(
      @NotNull UUID accountId, @NotNull BankAccountLifecycleRequest request) {
    BankAccount account = requireAccount(accountId);
    requireVersionMatch(account, request.version());
    BigDecimal balance = postingRepository.accountBalance(accountId);
    if (balance.signum() != 0) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_ACCOUNT_NOT_EMPTY,
          "Only an account with a zero balance can be closed",
          Map.of(
              "accountNo",
              account.getAccountNo(),
              "balance",
              balance.stripTrailingZeros().toPlainString()));
    }
    if (bankBookingRequestService.hasOpenRequests(accountId)) {
      throw new BankConflictException(
          BankConflictException.CODE_BANK_ACCOUNT_HAS_PENDING_REQUESTS,
          "The account has open booking requests; decide them before closing",
          Map.of("accountNo", account.getAccountNo()));
    }
    account.setStatus(BankAccountStatus.CLOSED);
    BankAccount saved = accountRepository.save(account);
    bankAuditService.record(
        BankAuditEventType.ACCOUNT_CLOSED, saved.getId(), null, null, saved.getAccountNo());
    return bankAccountMapper.toDto(saved, balance);
  }

  /**
   * Reopens a closed account, restoring full booking capability (REQ-BANK-002).
   *
   * @param accountId the account
   * @param request the echoed optimistic-locking version
   * @return the updated account
   * @throws NotFoundException when the account does not exist
   * @throws ObjectOptimisticLockingFailureException on a version mismatch (409)
   */
  @Transactional
  public BankAccountDto reopenAccount(
      @NotNull UUID accountId, @NotNull BankAccountLifecycleRequest request) {
    BankAccount account = requireAccount(accountId);
    requireVersionMatch(account, request.version());
    account.setStatus(BankAccountStatus.ACTIVE);
    BankAccount saved = accountRepository.save(account);
    bankAuditService.record(
        BankAuditEventType.ACCOUNT_REOPENED, saved.getId(), null, null, saved.getAccountNo());
    return bankAccountMapper.toDto(saved, postingRepository.accountBalance(accountId));
  }

  /**
   * Batch-computes the balances of many accounts as one grouped statement (REQ-DATA-003).
   *
   * @param accountIds the accounts
   * @return balance per account id; accounts without postings are absent (treat as zero)
   */
  public Map<UUID, BigDecimal> balancesFor(@NotNull List<UUID> accountIds) {
    if (accountIds.isEmpty()) {
      return Map.of();
    }
    return postingRepository.accountBalances(accountIds).stream()
        .collect(Collectors.toMap(BankAccountBalance::accountId, BankAccountBalance::balance));
  }

  /**
   * Resolves one booking row's transfer counter-leg from the batched legs and maps to the DTO.
   *
   * @param accountId the account whose history is rendered
   * @param row the projected booking row
   * @param legsByTx all legs of the page's transfer transactions, grouped by transaction
   * @return the booking DTO with counter-side labels for transfers
   */
  private BankBookingDto toBookingDto(
      @NotNull UUID accountId,
      @NotNull BankBookingRow row,
      @NotNull Map<UUID, List<BankCounterLeg>> legsByTx) {
    String counterAccountNo = null;
    String counterAccountName = null;
    String counterHolderHandle = null;
    boolean intraAccount = false;
    if (row.type() == BankTransactionType.TRANSFER) {
      List<BankCounterLeg> legs = legsByTx.getOrDefault(row.transactionId(), List.of());
      BankCounterLeg counter =
          legs.stream()
              .filter(l -> !l.postingId().equals(row.postingId()))
              .findFirst()
              .orElse(null);
      if (counter != null) {
        intraAccount = counter.accountId().equals(accountId);
        counterHolderHandle = counter.holderHandle();
        if (!intraAccount) {
          counterAccountNo = counter.accountNo();
          counterAccountName = counter.accountName();
        }
      }
    }
    return new BankBookingDto(
        row.postingId(),
        row.transactionId(),
        row.type(),
        row.amount(),
        row.holderHandle(),
        row.note(),
        row.createdAt(),
        row.reversedTransactionId(),
        counterAccountNo,
        counterAccountName,
        counterHolderHandle,
        intraAccount);
  }

  /**
   * Loads an account or fails with 404.
   *
   * @param accountId the account id
   * @return the account entity
   */
  private BankAccount requireAccount(@NotNull UUID accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new NotFoundException("Bank account not found"));
  }

  /**
   * Explicit optimistic-lock check (HangarService precedent): fail fast with the standard 409
   * before touching any property when the client echoed a stale version.
   *
   * @param account the loaded account
   * @param version the client-echoed version
   */
  private static void requireVersionMatch(@NotNull BankAccount account, @NotNull Long version) {
    if (account.getVersion() != null && !account.getVersion().equals(version)) {
      throw new ObjectOptimisticLockingFailureException(BankAccount.class, account.getId());
    }
  }

  /**
   * Rejects an org-unit reference on the types that carry none: {@code CARTEL_BANK} and {@code
   * SPECIAL}. {@code ORG_UNIT} (Staffel/SK), {@code AREA} (Bereich) and {@code CARTEL} (OL) all
   * carry the org_unit FK and validate it in their own switch branch (epic #692, REQ-ORG-019).
   *
   * @param request the creation payload
   */
  private static void requireNoOrgUnit(@NotNull CreateBankAccountRequest request) {
    if (request.orgUnitId() != null) {
      throw new BadRequestException(
          "A " + request.type() + " account must not carry an org unit reference");
    }
  }

  /**
   * Rejects a free-form area name. Since epic #692 (REQ-ORG-019) an AREA account is owned by its
   * Bereich via the org_unit FK, so no type accepts an area name on creation — the legacy {@code
   * areaName} form survives only for rows created before the FK and is never produced here.
   *
   * @param request the creation payload
   */
  private static void requireNoAreaName(@NotNull CreateBankAccountRequest request) {
    if (request.areaName() != null && !request.areaName().isBlank()) {
      throw new BadRequestException(
          "A " + request.type() + " account must not carry a free-form area name");
    }
  }
}
