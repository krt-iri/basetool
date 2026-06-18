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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.BankConflictException;
import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.BankAccountMapper;
import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.BankAuditEventType;
import de.greluc.krt.iri.basetool.backend.model.BankTransactionType;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.dto.BankAccountDetailDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankAccountDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankBookingDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankCapabilitiesDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankHolderBalanceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.request.BankAccountLifecycleRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.CreateBankAccountRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.request.RenameBankAccountRequest;
import de.greluc.krt.iri.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.iri.basetool.backend.model.projection.BankBookingRow;
import de.greluc.krt.iri.basetool.backend.model.projection.BankCounterLeg;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
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
        if (accountRepository.existsByOrgUnitId(orgUnit.getId())) {
          throw new DuplicateEntityException("The org unit already owns a bank account");
        }
        account.setOrgUnit(orgUnit);
      }
      case AREA -> {
        if (request.areaName() == null || request.areaName().isBlank()) {
          throw new BadRequestException("An AREA account requires an area name");
        }
        requireNoOrgUnit(request);
        account.setAreaName(request.areaName().trim());
      }
      case CARTEL, CARTEL_BANK -> {
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
   * Rejects an org-unit reference on non-{@code ORG_UNIT} types.
   *
   * @param request the creation payload
   */
  private static void requireNoOrgUnit(@NotNull CreateBankAccountRequest request) {
    if (request.orgUnitId() != null) {
      throw new BadRequestException("Only an ORG_UNIT account may carry an org unit reference");
    }
  }

  /**
   * Rejects an area name on non-{@code AREA} types.
   *
   * @param request the creation payload
   */
  private static void requireNoAreaName(@NotNull CreateBankAccountRequest request) {
    if (request.areaName() != null && !request.areaName().isBlank()) {
      throw new BadRequestException("Only an AREA account may carry an area name");
    }
  }
}
